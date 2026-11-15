package com.barisozy.cleanarchitecture.domain.user;

/**
 * The role of a user.
 *
 * <p>The template has exactly two roles, so a role is a closed set and not a row in a table. An
 * administration feature that needs more roles replaces this enum with a role aggregate; until
 * then, the enum keeps the whole authorization model in one compile-time-checked place.
 */
public enum RoleName {

    /** A regular user: full CRUD on the todos it owns, read-only access to nothing else. */
    USER,

    /** An administrator: reads every todo, writes only its own. */
    ADMIN
}
