package moe.hhm.shiori.product.chat.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.error.ProductErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.product.chat.config.ChatTicketProperties;
import moe.hhm.shiori.product.chat.dto.ChatTicketResponse;
import moe.hhm.shiori.product.model.ProductRecord;
import moe.hhm.shiori.product.repository.ProductMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChatTicketService {

    private final ChatTicketProperties properties;
    private final ProductMapper productMapper;

    private volatile RSAPrivateKey privateKey;

    public ChatTicketService(ChatTicketProperties properties, ProductMapper productMapper) {
        this.properties = properties;
        this.productMapper = productMapper;
    }

    public ChatTicketResponse issueTicket(Long listingId, Long buyerId) {
        if (listingId == null || listingId <= 0) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }

        Long sellerId = productMapper.findOnSaleOwnerUserIdByProductId(listingId);
        if (sellerId == null) {
            ProductRecord product = productMapper.findProductById(listingId);
            if (product == null || (product.isDeleted() != null && product.isDeleted() == 1)) {
                throw new BizException(ProductErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND);
            }
            throw new BizException(ProductErrorCode.PRODUCT_NOT_ON_SALE, HttpStatus.BAD_REQUEST);
        }
        if (buyerId.equals(sellerId)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }

        Instant now = Instant.now();
        long ttlSeconds = properties.getTtlSeconds() > 0 ? properties.getTtlSeconds() : 300;
        Instant expireAt = now.plusSeconds(ttlSeconds);
        String jti = UUID.randomUUID().toString();

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(String.valueOf(buyerId))
                .issuer(properties.getIssuer())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expireAt))
                .jwtID(jti)
                .claim("buyerId", buyerId)
                .claim("sellerId", sellerId)
                .claim("listingId", listingId)
                .build();

        SignedJWT signedJWT = new SignedJWT(buildHeader(), claimsSet);
        try {
            signedJWT.sign(new RSASSASigner(resolvePrivateKey()));
        } catch (JOSEException e) {
            throw new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }

        return new ChatTicketResponse(
                signedJWT.serialize(),
                expireAt.toString(),
                buyerId,
                sellerId,
                listingId,
                jti
        );
    }

    private JWSHeader buildHeader() {
        JWSHeader.Builder builder = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT);
        if (StringUtils.hasText(properties.getKid())) {
            builder.keyID(properties.getKid().trim());
        }
        return builder.build();
    }

    private RSAPrivateKey resolvePrivateKey() {
        if (privateKey != null) {
            return privateKey;
        }
        synchronized (this) {
            if (privateKey != null) {
                return privateKey;
            }
            privateKey = parsePrivateKey(properties.getPrivateKeyPemBase64());
            return privateKey;
        }
    }

    private RSAPrivateKey parsePrivateKey(String privateKeyPemBase64) {
        if (!StringUtils.hasText(privateKeyPemBase64)) {
            throw new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
        try {
            byte[] pemBytes = Base64.getDecoder().decode(privateKeyPemBase64.trim());
            String pem = new String(pemBytes, StandardCharsets.UTF_8);
            String normalized = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] der = Base64.getDecoder().decode(normalized);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PrivateKey key = keyFactory.generatePrivate(spec);
            if (key instanceof RSAPrivateKey rsaPrivateKey) {
                return rsaPrivateKey;
            }
            throw new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
