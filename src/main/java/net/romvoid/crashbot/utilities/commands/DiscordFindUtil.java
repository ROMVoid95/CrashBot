package net.romvoid.crashbot.utilities.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.utils.concurrent.Task;
import net.dv8tion.jda.internal.utils.concurrent.task.GatewayTask;
import net.romvoid.crashbot.core.modules.commands.base.Context;

@UtilityClass
public class DiscordFindUtil {
    private final static Pattern DISCORD_ID = Pattern.compile("\\d{17,20}"); // ID
    private final static Pattern FULL_USER_REF = Pattern.compile("(.{2,32})\\s*#(\\d{4})"); // $1 -> username, $2 -> discriminator
    private final static Pattern USER_MENTION = Pattern.compile("<@!?(\\d{17,20})>"); // $1 -> ID

    /**
     * This takes the result of the Async call of Guild#retrieveMembersByPrefix and parses it.
     * This is VERY hacky. Like **VERY**, but async is hard.
     * @param query The original query used to find the members.
     * @param result The result of Guild#retrieveMembersByPrefix
     * @return The member found. Returns null if nothing was found.
     */
    public static Member findMember(String query, List<Member> result, Context ctx) {
        // This is technically a safeguard, shouldn't be needed, but since we handle no results by giving this an empty list, it should be done.
        // If you want to handle it differently, there's findMemberDefault to return a default member.
        if (result.isEmpty()) {
            ctx.send(EmoteReference.ERROR + "Cannot find any member with that name :(");
            return null;
        }

        // Mention
        // On mention, due to the handler implementation we're only gonna get ONE result, as the handler makes sure we do get it properly.
        // If there's no result, well, heck.
        Matcher userMention = USER_MENTION.matcher(query);
        if (userMention.matches() && ctx.getMentionedMembers().size() > 0) {
            return result.get(0);
        }

        // User ID
        // On user id, due to the handler implementation we're only gonna get ONE result, so use it.
        // This is to avoid multiple requests to discord.
        if (DISCORD_ID.matcher(query).matches()) {
            return result.get(0);
        }

        // For user#discriminator searches and username searches we actually do need to send a request to get the members by
        // prefix to discord, without any consideration to cache. This is a little expensive but should be fine.

        // user#discriminator search
        var fullRefMatch = FULL_USER_REF.matcher(query);
        if (fullRefMatch.matches()) {
            // We handle name elsewhere.
            var disc = fullRefMatch.replaceAll("$2");
            if (result.isEmpty()) {
                ctx.send(EmoteReference.ERROR + "Cannot find any member with that name :(");
                return null;
            }

            for(var member : result) {
                if (member.getUser().getDiscriminator().equals(disc)) {
                    return member;
                }
            }

            ctx.send(EmoteReference.ERROR + "Cannot find any member with that name :(");
            return null;
        }
        // end of user#discriminator search

        // Filter member results: usually we just want exact search, but partial matches are possible and allowed.
        var found = filterMemberResults(result, query);

        // We didn't find anything *after* filtering.
        if (found.isEmpty()) {
            ctx.send(EmoteReference.ERROR + "Cannot find any member with that name :(");
            return null;
        }

        // Too many results, display results and move on.
//        if (found.size() > 1) {
//            ctx.sendFormat("%sToo many users found, maybe refine your search? (ex. use name#discriminator)\n**Users found:** %s",
//                    EmoteReference.THINKING,
//                    found.stream().limit(7).map(m -> m.getUser().getName() + "#" + m.getUser().getDiscriminator())
//                            .collect(Collectors.joining(", "))
//            );
//
//            return null;
//        }

        // Return the first object. In this case it would be the only one, and that is the search result.
        return found.get(0);
    }

    private static List<Member> filterMemberResults(List<Member> result, String query) {
        ArrayList<Member> exact = new ArrayList<>();
        ArrayList<Member> wrongCase = new ArrayList<>();
        ArrayList<Member> startsWith = new ArrayList<>();
        ArrayList<Member> contains = new ArrayList<>();

        var lowerQuery = query.toLowerCase();

        result.forEach(member -> {
            String name = member.getUser().getName();
            String effName = member.getEffectiveName();

            if (name.equals(query) || effName.equals(query)) {
                exact.add(member);
            } else if ((name.equalsIgnoreCase(query) || effName.equalsIgnoreCase(query)) && exact.isEmpty()) {
                wrongCase.add(member);
            } else if ((name.toLowerCase().startsWith(lowerQuery) || effName.toLowerCase().startsWith(lowerQuery)) && wrongCase.isEmpty()) {
                startsWith.add(member);
            } else if ((name.toLowerCase().contains(lowerQuery) || effName.toLowerCase().contains(lowerQuery)) && startsWith.isEmpty()) {
                contains.add(member);
            }
        });

        List<Member> found;

        // Slowly becoming insane.png
        if (!exact.isEmpty()) {
            found = Collections.unmodifiableList(exact);
        } else if (!wrongCase.isEmpty()) {
            found = Collections.unmodifiableList(wrongCase);
        } else if (!startsWith.isEmpty()) {
            found = Collections.unmodifiableList(startsWith);
        } else {
            found = Collections.unmodifiableList(contains);
        }

        return found;
    }

    public static Member findMemberDefault(String query, List<Member> result, Context ctx, Member member) {
        if (query.isEmpty()) {
            return member;
        } else {
            return findMember(query, result, ctx);
        }
    }

    // This whole thing is hacky as FUCK
    public static Task<List<Member>> lookupMember(Guild guild, Context context, String query) {
        if (query.trim().isEmpty()) {
            // This is next-level hacky, LMAO.
            // Basically we handle giving an empty value to this, and just return an empty list in that case.
            return emptyMemberTask();
        }

        // Handle user mentions.
        if (USER_MENTION.matcher(query).matches() && context.getMentionedMembers().size() > 0) {
            if (context.getMentionedMembers().size() > 1) {
                context.send("general.too_many_mentions");
                return emptyMemberTask();
            }

            // If we get a user mention we actually DO get a "fake" member and can use it.
            // This avoids sending a new request to discord completely.
            CompletableFuture<List<Member>> result = new CompletableFuture<>();
            result.complete(Collections.singletonList(context.getMentionedMembers().get(0)));
            return new GatewayTask<>(result, () -> {});
        }

        // User ID
        if (DISCORD_ID.matcher(query).matches()) {
            // If we get a user ID we can actually look it up *once* instead of sending two requests to discord.
            // Using getMemberByPrefix with an ID will actually cause it to do two API requests, reduce this to just one.
            // The member can actually be cached and TTL'd by JDA when the member leaves (having GUILD_MEMBERS intent),
            // so this result could and probably will be from the cache,
            // or the lookup will only happen once, which is very cheap and good.
            CompletableFuture<List<Member>> result = new CompletableFuture<>();

            var member = context.retrieveMemberById(query, false);
            if (member == null) {
                return emptyMemberTask();
            }

            result.complete(Collections.singletonList(member));
            return new GatewayTask<>(result, () -> {});
        }

        // Usually people like to mess with results by searching for stuff like "a" and "tu", stuff like that.
        // This just makes sure we don't send a request to discord for useless searches.
        if (query.length() < 4) {
            context.send("general.query_too_small");
            return emptyMemberTask();
        }

        // The only two cases where we actually need to send retrieveMembersByPrefix to discord is when we get either a
        // username search or a username#discriminator search. This isn't exactly cheap, but we can work with it, I guess.

        // username#discriminator regex matcher.
        var fullRefMatch = FULL_USER_REF.matcher(query);
        if (fullRefMatch.matches()) {
            // Retrieve just the name, as there will be no result with discriminator, we need to filter that later.
            var name = fullRefMatch.replaceAll("$1");
            return guild.retrieveMembersByPrefix(name, 5);
        } else {
            return guild.retrieveMembersByPrefix(query, 5);
        }
    }

    private static Task<List<Member>> emptyMemberTask() {
        CompletableFuture<List<Member>> result = new CompletableFuture<>();
        result.complete(Collections.emptyList());
        return new GatewayTask<>(result, () -> {});
    }
}