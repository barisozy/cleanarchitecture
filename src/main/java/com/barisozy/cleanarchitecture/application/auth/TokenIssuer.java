package com.barisozy.cleanarchitecture.application.auth;

import com.barisozy.cleanarchitecture.domain.user.User;

/**
 * Issues the credential of an authenticated user.
 *
 * <p>The application decides <em>when</em> a token is issued; the infrastructure decides
 * <em>how</em> it is signed. That keeps the JOSE library and the signing algorithm out of the use
 * cases, and a test can replace this port with a fake.
 */
public interface TokenIssuer {

    AuthResult issue(User user);
}
