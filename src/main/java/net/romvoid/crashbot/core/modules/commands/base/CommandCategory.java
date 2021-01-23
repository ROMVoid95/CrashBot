/*
 * Copyright (C) 2016-2021 David Rubio Escares / Kodehawa
 *
 *  Mantaro is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  Mantaro is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Mantaro. If not, see http://www.gnu.org/licenses/
 */

package net.romvoid.crashbot.core.modules.commands.base;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.romvoid.crashbot.utilities.Commons;

public enum CommandCategory {
    ACTION(CommandPermission.USER, "categories.action", "Action"),
    MODERATION(CommandPermission.ADMIN, "categories.moderation", "Moderation"),
    OWNER(CommandPermission.OWNER, "categories.owner", "Owner"),
    INFO(CommandPermission.USER, "categories.info", "Info"),
    UTILS(CommandPermission.USER, "categories.utils", "Utility"),
    MISC(CommandPermission.USER, "categories.misc", "Misc");
	
    public final CommandPermission permission;
    private final String s;
    private final String qualifiedName;

    CommandCategory(CommandPermission p, String s, String name) {
        this.permission = p;
        this.s = s;
        this.qualifiedName = name;
    }

    /**
     * Looks up the Category based on a String value, if nothing is found returns null.
     * *
     *
     * @param name The String value to match
     * @return The category, or null if nothing is found.
     */
    public static CommandCategory lookupFromString(String name) {
        for (CommandCategory cat : CommandCategory.values()) {
            if (cat.qualifiedName.equalsIgnoreCase(name)) {
                return cat;
            }
        }
        return null;
    }

    /**
     * @return The name of the category.
     */
    public static List<String> getAllNames() {
        return Stream.of(CommandCategory.values()).map(category -> Commons.capitalize(category.qualifiedName.toLowerCase())).collect(Collectors.toList());
    }

    /**
     * @return All categories as a List. You could do Category#values anyway, this is just for my convenience.
     */
    public static List<CommandCategory> getAllCategories() {
        return Arrays.asList(CommandCategory.values());
    }

    @Override
    public String toString() {
        return s;
    }
}