package com.qijianguo.micro.services.user.domain.captcha.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.qijianguo.micro.services.base.exception.BusinessException;
import com.qijianguo.micro.services.user.domain.captcha.entity.Captcha;
import com.qijianguo.micro.services.user.domain.captcha.entity.Token;

import com.qijianguo.micro.services.user.domain.captcha.exception.CaptchaEmBusinessError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author qijianguo
 */
@Service("tokenCaptchaDomainService")
public class TokenCaptchaDomainService extends CaptchaDomainService<Token> {

    private static final Logger logger = LoggerFactory.getLogger(TokenCaptchaDomainService.class);

    @Value(value = "$token.secret")
    private String tokenSecret;

    @Override
    public void create(Captcha captcha) {
        Token token = captcha.getToken();

        if (token == null) {
            throw new BusinessException(CaptchaEmBusinessError.CAPTCHA_TOKEN_NOT_INIT);
        }
        String oldToken = token.getValue();
        if (!StringUtils.isEmpty(oldToken)) {
            delete(oldToken);
        }
        long t = System.currentTimeMillis();
        if (token.getTimeUnit() == TimeUnit.DAYS) {
            t += token.getTimeout() * 1000 * 60 * 24;
        } else if (token.getTimeUnit() == TimeUnit.HOURS) {
            t += token.getTimeout() * 1000 * 60;
        } else if (token.getTimeUnit() == TimeUnit.SECONDS) {
            t += token.getTimeout() * 1000;
        } else {
            t += token.getTimeout();
        }
        Date date = new Date(t);
        Algorithm algorithm = Algorithm.HMAC256(tokenSecret);
        Map<String, Object> headers = new HashMap<>(2);
        headers.put("type", "JWT");
        headers.put("alg", "HS256");
        String value = JWT.create().withHeader(headers)
                .withClaim("token", token.getKey())
                .withExpiresAt(date)
                .sign(algorithm);

        token.setValue(value);

        save(token.getKey(), token, token.getTimeout(), token.getTimeUnit());
    }

    @Override
    public void verify(Captcha captcha) {
        Token token = captcha.getToken();
        Token tokenUser = getPrincipal(token);
        if (tokenUser != null) {
            // 校验
            Token fromCache = get(tokenUser.getKey());
            if (fromCache != null) {
                String old = fromCache.getValue();
                // 只允许最新的Token去登录
                if (Objects.equals(old, token.getValue())) {
                    // 延长时间
                    refresh(fromCache.getKey(),
                            token.getTimeout() !=0 ? token.getTimeout() : fromCache.getTimeout(),
                            token.getTimeUnit() != null ? token.getTimeUnit() : fromCache.getTimeUnit());
                    return;
                }
            }
        }
        throw new BusinessException(CaptchaEmBusinessError.TOKEN_EXPIRED);
    }

    public Token getPrincipal(Token token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(tokenSecret);
            JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT jwt = verifier.verify(token.getValue());
            String id = jwt.getClaim("token").asString();
            return new Token(id);
        } catch (Exception ignore) {
            logger.error(ignore.getMessage(), ignore);
        }
        return null;
    }
}
