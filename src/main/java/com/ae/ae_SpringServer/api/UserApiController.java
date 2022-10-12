package com.ae.ae_SpringServer.api;

import com.ae.ae_SpringServer.config.BaseResponse;
import com.ae.ae_SpringServer.config.security.JwtProvider;
import com.ae.ae_SpringServer.domain.User;
import com.ae.ae_SpringServer.dto.request.SignupRequestDto;
import com.ae.ae_SpringServer.dto.request.UserSocialLoginRequestDto;
import com.ae.ae_SpringServer.dto.request.UserUpdateRequestDto;
import com.ae.ae_SpringServer.dto.response.AppleLoginResponse;
import com.ae.ae_SpringServer.dto.response.LoginResponseDto;
import com.ae.ae_SpringServer.dto.response.UserInfoResponseDto;
import com.ae.ae_SpringServer.service.UserService;
import com.nimbusds.jose.shaded.json.JSONObject;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

import static com.ae.ae_SpringServer.config.BaseResponseStatus.*;

@RestController
@RequiredArgsConstructor
public class UserApiController {
    private final UserService userService;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    //[POST] 4-1 카카오 로그인
    // 로그인 시에, kakaoprofile로 받은 정보가 db에 있으면 jwt 토큰 발급(status코드는 온보딩 안띄우게). db에 없으면 new user로 저장시키고 jwt 토큰발급(온보딩 띄우게)
    @PostMapping("/api/login")
    public  BaseResponse<LoginResponseDto> loginByKakao(
            @RequestBody UserSocialLoginRequestDto socialLoginRequestDto) {
        String token = socialLoginRequestDto.getAccessToken();
        // KakaoProfile kakaoProfile = kakaoService.getKakaoProfile(token);

        /*
        if (kakaoProfile.getKakao_account().getEmail() == null) {
            kakaoService.kakaoUnlink(socialSignupRequestDto.getAccessToken());
            throw new CSocialAgreementException();
        }
         */
        RestTemplate restTemplate = new RestTemplate();
        URI uri = URI.create("https://kapi.kakao.com/v2/user/me");
        HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        MultiValueMap<String, Object> pa = new LinkedMultiValueMap<>();
        HttpEntity<MultiValueMap<String, Object>> restRequest = new HttpEntity<>(pa, headers);
        restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory());
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            public boolean hasError(ClientHttpResponse response) throws IOException {
                HttpStatus statusCode = response.getStatusCode();
                return statusCode.series() == HttpStatus.Series.SERVER_ERROR;
            }
        });
        ResponseEntity<JSONObject> apiResponse = restTemplate.postForEntity(uri, restRequest, JSONObject.class);
        JSONObject responseBody = apiResponse.getBody();

        String id = String.valueOf(responseBody.get("id"));

        Optional<User> user = userService.findByKakaoId(id);
        boolean isEmpty = user.isEmpty();
        System.out.println(isEmpty);
        if(!isEmpty) {
            return new BaseResponse<>(new LoginResponseDto(user.get().getId(), jwtProvider.createToken(user.get()), false));
        } else {
            User u = User.createUser(id);
            userService.create(u);
            return new BaseResponse<>(new LoginResponseDto(u.getId(), jwtProvider.createToken(u), true));
        }
    }

    //[POST] 4-2 : 애플로그인 api
    @PostMapping("/api/apple-login")
    public BaseResponse<LoginResponseDto> loginByApple(@RequestBody UserSocialLoginRequestDto socialLoginRequestDto){
        return new BaseResponse<>(userService.login(socialLoginRequestDto));

    }

    // [POST] 3-3  회원 등록
    @PostMapping("/api/signup")
    public BaseResponse<String> signup(@AuthenticationPrincipal String userId, @RequestBody SignupRequestDto signupRequestDto) {
        if(userId.equals("INVALID JWT")){
            return new BaseResponse<>(INVALID_JWT);
        }
        if(userId == null) {
            return new BaseResponse<>(EMPTY_JWT);
        }
        User user = userService.findOne(Long.valueOf(userId));
        if (user == null) {
            return new BaseResponse<>(INVALID_JWT);
        }
        if(signupRequestDto.getName().isEmpty() || signupRequestDto.getName().equals("")) {
            return new BaseResponse<>(POST_USER_NO_NAME);
        }
        if(signupRequestDto.getName().length() > 45) {
            return new BaseResponse<>(POST_USER_LONG_NAME);
        }
        if(signupRequestDto.getAge() < 1) {
            return new BaseResponse<>(POST_USER_MINUS_AGE);
        }
        if(signupRequestDto.getGender() != 0 && signupRequestDto.getGender() != 1) {
            return new BaseResponse<>(POST_USER_INVALID_GENDER);
        }

        if(signupRequestDto.getHeight().isEmpty() || signupRequestDto.getHeight().equals("")) {
            return new BaseResponse<>(POST_USER_NO_HEIGHT);
        }

        if(Integer.parseInt(signupRequestDto.getHeight()) < 0) {
            return new BaseResponse<>(POST_USER_MINUS_HEIGHT);
        }

        if(signupRequestDto.getWeight().isEmpty() || signupRequestDto.getWeight().equals("")) {
            return new BaseResponse<>(POST_USER_NO_WEIGHT);
        }

        if(Integer.parseInt(signupRequestDto.getWeight()) < 0) {
            return new BaseResponse<>(POST_USER_MINUS_WEIGHT);
        }
        if(signupRequestDto.getActivity() != 25 && signupRequestDto.getActivity() != 33 && Integer.valueOf(signupRequestDto.getActivity()) != 40) {
            return new BaseResponse<>(POST_USER_INVALID_ACTIVITY);
        }
        userService.signup(Long.valueOf(userId), signupRequestDto);
        return new BaseResponse<>(userId + "번  회원 등록되었습니다");
    }

    // [GET] 3-1 회원 정보 조회
    @GetMapping("/api/userinfo")
    public BaseResponse<UserInfoResponseDto> info(@AuthenticationPrincipal String userId) {
        if(userId.equals("INVALID JWT")){
            return new BaseResponse<>(INVALID_JWT);
        }
        if(userId == null) {
            return new BaseResponse<>(EMPTY_JWT);
        }
        User user = userService.findOne(Long.valueOf(userId));
        if (user == null) {
            return new BaseResponse<>(INVALID_JWT);
        }
        return new BaseResponse<>(new UserInfoResponseDto(user.getName(), user.getGender(), user.getAge(), user.getHeight(), user.getWeight(), user.getIcon(), user.getActivity()));

    }

    // [PUT] 3-2 회원 정보 수정
    @PutMapping("/api/userupdate")
    public BaseResponse<String>  update(@AuthenticationPrincipal String userId, @RequestBody UserUpdateRequestDto userUpdateRequestDto) {
        if(userId.equals("INVALID JWT")){
            return new BaseResponse<>(INVALID_JWT);
        }
        if(userId == null) {
            return new BaseResponse<>(EMPTY_JWT);
        }
        User user = userService.findOne(Long.valueOf(userId));
        if (user == null) {
            return new BaseResponse<>(INVALID_JWT);
        }
        if(userUpdateRequestDto.getAge() < 1) {
            return new BaseResponse<>(PUT_USER_MINUS_AGE);
        }

        if(userUpdateRequestDto.getHeight().isEmpty() || userUpdateRequestDto.getHeight().equals("")) {
            return new BaseResponse<>(PUT_USER_NO_HEIGHT);
        }

        if(Integer.parseInt(userUpdateRequestDto.getHeight()) < 0) {
            return new BaseResponse<>(PUT_USER_MINUS_HEIGHT);
        }

        if(userUpdateRequestDto.getWeight().isEmpty() || userUpdateRequestDto.getWeight().equals("")) {
            return new BaseResponse<>(PUT_USER_NO_WEIGHT);
        }

        if(Integer.parseInt(userUpdateRequestDto.getWeight()) < 0) {
            return new BaseResponse<>(PUT_USER_MINUS_WEIGHT);
        }
        if(Integer.valueOf(userUpdateRequestDto.getActivity()) != 25 && Integer.valueOf(userUpdateRequestDto.getActivity()) != 33 && Integer.valueOf(userUpdateRequestDto.getActivity()) != 40) {
            return new BaseResponse<>(PUT_USER_INVALID_ACTIVITY);
        }

        userService.update(Long.valueOf(userId), userUpdateRequestDto);
        return new BaseResponse<>(userId + "번  회원 정보 수정되었습니다");
    }

    // [DELETE] 3-4 회원 탈퇴
    @DeleteMapping("/api/userdelete")
    public BaseResponse<String> deleteUser(@AuthenticationPrincipal String userId) {
        if(userId.equals("INVALID JWT")){
            return new BaseResponse<>(INVALID_JWT);
        }
        if(userId == null) {
            return new BaseResponse<>(EMPTY_JWT);
        }
        User user = userService.findOne(Long.valueOf(userId));
        if(user == null) {
            return new BaseResponse<>(INVALID_JWT);
        }

        userService.delete(Long.valueOf(userId));
        return new BaseResponse<>("회원 탈퇴 되었습니다.");
    }


    /*
    // 액세스 토큰 만료시 회원 검증 후 리프레쉬 토큰을 검증해서 액세스 토큰과 리프레시 토큰을 재발급함
    @PostMapping("/reissue")
    public SingleResult<TokenDto> reissue(
            @ApiParam(value = "토큰 재발급 요청 DTO", required = true)
            @RequestBody TokenRequestDto tokenRequestDto) {
        return responseService.getSingleResult(signService.reissue(tokenRequestDto));
    }
     */




}
