package com.company.mailing.feign;

import com.company.mailing.dto.auth.SessionCheckReq;
import com.company.mailing.dto.auth.SessionCheckResp;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "AuthClient", url = "${security.auth.service-url:http://192.168.1.96:8081}")
public interface AuthClient {

    @GetMapping("/hasUser/{userID}")
    Boolean checkPresentUser(
            @PathVariable("userID") UUID userId,
            @RequestHeader("Authorization") String token
    );

    @PostMapping("/internal/session/check")
    SessionCheckResp check(@RequestBody SessionCheckReq req);
}
