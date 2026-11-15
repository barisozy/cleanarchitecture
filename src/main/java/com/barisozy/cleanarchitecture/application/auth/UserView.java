package com.barisozy.cleanarchitecture.application.auth;

import com.barisozy.cleanarchitecture.domain.user.RoleName;
import com.barisozy.cleanarchitecture.domain.user.User;
import com.barisozy.cleanarchitecture.domain.user.UserId;

/** The profile of a user as the application exposes it. */
public record UserView(UserId id, String email, RoleName role) {

    public static UserView from(User user) {
        return new UserView(user.id(), user.email().value(), user.role());
    }
}
