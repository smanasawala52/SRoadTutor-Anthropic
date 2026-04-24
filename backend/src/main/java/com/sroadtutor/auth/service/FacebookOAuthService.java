package com.sroadtutor.auth.service;

import com.restfb.DefaultFacebookClient;
import com.restfb.FacebookClient;
import com.restfb.Parameter;
import com.restfb.Version;
import com.restfb.exception.FacebookException;
import com.restfb.types.User;
import com.sroadtutor.config.AppProperties;
import com.sroadtutor.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Function;

/**
 * Verifies a Facebook user access token by:
 *  1) calling Graph API {@code /me?fields=id,email,name} with the token
 *  2) rejecting if the {@code id} comes back empty.
 *
 * <p>Email can be null if the user didn't grant that permission — we
 * synthesise a placeholder {@code facebook_<id>@users.noreply.sroadtutor.app}
 * so the user can still sign in.</p>
 */
@Service
public class FacebookOAuthService {

    private static final Logger log = LoggerFactory.getLogger(FacebookOAuthService.class);
    private static final String PLACEHOLDER_EMAIL_SUFFIX = "@users.noreply.sroadtutor.app";

    private final AppProperties props;
    private final Function<String, FacebookClient> clientFactory;

    public FacebookOAuthService(AppProperties props) {
        this(props, token -> new DefaultFacebookClient(token, Version.LATEST));
    }

    /** Package-private ctor used by tests to inject a mock client factory. */
    FacebookOAuthService(AppProperties props, Function<String, FacebookClient> clientFactory) {
        this.props = props;
        this.clientFactory = clientFactory;
    }

    public OAuthVerifier verify(String userAccessToken) {
        try {
            FacebookClient fb = clientFactory.apply(userAccessToken);
            User me = fb.fetchObject("me", User.class, Parameter.with("fields", "id,email,name"));
            if (me == null || me.getId() == null || me.getId().isBlank()) {
                throw new UnauthorizedException("INVALID_FACEBOOK_TOKEN", "Facebook token did not resolve to a user");
            }
            String email = me.getEmail();
            if (email == null || email.isBlank()) {
                email = "facebook_" + me.getId() + PLACEHOLDER_EMAIL_SUFFIX;
                log.info("Facebook user {} did not share email; using placeholder", me.getId());
            }
            // App-secret-proof tightens things further but requires appsecret_proof header;
            // keep this note as a TODO for hardening.
            if (props.oauth().facebook().appSecret().isBlank()) {
                log.warn("Facebook app secret not set — skipping app-secret-proof check.");
            }
            return OAuthVerifier.of(me.getId(), email, me.getName());
        } catch (FacebookException ex) {
            log.warn("Facebook token verify error: {}", ex.getMessage());
            throw new UnauthorizedException("INVALID_FACEBOOK_TOKEN", "Could not verify Facebook access token");
        }
    }
}
