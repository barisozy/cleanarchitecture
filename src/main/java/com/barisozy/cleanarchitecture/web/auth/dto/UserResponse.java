package com.barisozy.cleanarchitecture.web.auth.dto;

import com.barisozy.cleanarchitecture.application.auth.UserView;
import com.barisozy.cleanarchitecture.domain.user.RoleName;
import java.util.UUID;

/** The profile of a user as the API exposes it. */
public record UserResponse(UUID id, String email, RoleName role) {

    public static UserResponse from(UserView view) {
        return new UserResponse(view.id().value(), view.email(), view.role());
    }
}
