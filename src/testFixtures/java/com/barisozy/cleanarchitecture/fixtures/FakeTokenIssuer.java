package com.barisozy.cleanarchitecture.fixtures;

import com.barisozy.cleanarchitecture.application.auth.AuthResult;
import com.barisozy.cleanarchitecture.application.auth.TokenIssuer;
import com.barisozy.cleanarchitecture.application.auth.UserView;
import com.barisozy.cleanarchitecture.domain.user.User;

/**
 * A token issuer for the service tests.
 *
 * <p>The services only need to know that a token was issued, not how it is signed. Testing them
 * against this fake keeps BCrypt, JOSE and the clock out of the unit tests.
 */
public final class FakeTokenIssuer implements TokenIssuer {

    public static final String TOKEN = "test-access-token";

    private User lastUser;

    @Override
    public AuthResult issue(User user) {
        lastUser = user;
        return new AuthResult(TOKEN, "Bearer", AuthTestData.TTL.toSeconds(), UserView.from(user));
    }

    /** The user of the last issued token, so that a test can assert on the flow. */
    public User lastUser() {
        return lastUser;
    }
}
