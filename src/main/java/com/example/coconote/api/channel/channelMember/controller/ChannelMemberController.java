package com.example.coconote.api.channel.channelMember.controller;

import com.example.coconote.api.channel.channelMember.dto.request.ChannelMemberRoleReqDto;
import com.example.coconote.api.channel.channelMember.dto.response.ChannelMemberListResDto;
import com.example.coconote.api.channel.channelMember.service.ChannelMemberService;
import com.example.coconote.common.CommonResDto;
import com.example.coconote.security.util.CustomPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ChannelMemberController {
    private final ChannelMemberService channelMemberService;
    @Autowired
    public ChannelMemberController(ChannelMemberService channelMemberService) {
        this.channelMemberService = channelMemberService;
    }

    @Operation(summary= "채널 회원 생성(가입)")
    @PostMapping("/channel/member/create/{channelId}") // 채널 가입
    public ResponseEntity<Object> channelMemberCreate(@PathVariable Long channelId, @AuthenticationPrincipal CustomPrincipal customPrincipal) {
        ChannelMemberListResDto resDto = channelMemberService.channelMemberCreate(channelId, customPrincipal.getEmail());
        CommonResDto commonResDto = new CommonResDto(HttpStatus.CREATED, "member is successfully created", resDto);
        return new ResponseEntity<>(commonResDto, HttpStatus.CREATED);
    }

    @Operation(summary= "채널 회원 생성 (초대)")
    @PostMapping("/channel/member/invite/{channelId}") // 채널 초대
    public ResponseEntity<Object> channelMemberInvite(@PathVariable Long channelId, @RequestParam Long workspaceMemberId, @AuthenticationPrincipal CustomPrincipal customPrincipal) {
        ChannelMemberListResDto resDto = channelMemberService.channelMemberInvite(channelId, workspaceMemberId, customPrincipal.getEmail());
        CommonResDto commonResDto = new CommonResDto(HttpStatus.CREATED, "member is successfully invited", resDto);
        return new ResponseEntity<>(commonResDto, HttpStatus.CREATED);
    }

    @Operation(summary= "채널 회원 목록 조회")
    @GetMapping("/channel/member/list/{channelId}") // 채널 유저 목록 조회
    public ResponseEntity<Object> channelMemberRead(@PathVariable Long channelId) {
        List<ChannelMemberListResDto> dtos = channelMemberService.channelMemberList(channelId);
        CommonResDto commonResDto = new CommonResDto(HttpStatus.OK, "list is successfully found", dtos);
        return new ResponseEntity<>(commonResDto, HttpStatus.OK);
    }


    @Operation(summary= "채널 관리자 권한 부여/박탈")
    @PatchMapping("/channel/member/role") // 채널 관리자 권한 부여/삭제
    public ResponseEntity<Object> channelMemberChangeRole(@RequestBody ChannelMemberRoleReqDto dto, @AuthenticationPrincipal CustomPrincipal customPrincipal) {
        ChannelMemberListResDto resDto = channelMemberService.channelMemberChangeRole(dto, customPrincipal.getEmail());
        CommonResDto commonResDto = new CommonResDto(HttpStatus.OK, "role is successfully changed", resDto);
        return new ResponseEntity<>(commonResDto, HttpStatus.OK);
    }

    @Operation(summary= "채널 즐겨찾기 추가/해제")
    @PatchMapping("/channel/member/bookmark/{channelId}")  // 채널 즐겨찾기
    public ResponseEntity<Object> channelBookmark(@PathVariable Long channelId, @AuthenticationPrincipal CustomPrincipal customPrincipal) {
        CommonResDto commonResDto;
        Boolean value = channelMemberService.channelBookmark(channelId, customPrincipal.getEmail());
        if(value) {
            commonResDto = new CommonResDto(HttpStatus.OK, "bookmark is successfully added", value);
        }else{
            commonResDto = new CommonResDto(HttpStatus.OK, "bookmark is successfully deleted", value);
        }
        return new ResponseEntity<>(commonResDto, HttpStatus.OK);
    }

    @Operation(summary= "채널 회원 강퇴")
    @DeleteMapping("/channel/member/delete/{id}") // 채널 강퇴
    public ResponseEntity<Object> channelMemberDelete(@PathVariable Long id, @AuthenticationPrincipal CustomPrincipal customPrincipal) {
        channelMemberService.channelMemberDelete(id, customPrincipal.getEmail()); // 1번 파라미터는 삭제당하는 사람, 2번은 삭제하는 사람
        CommonResDto commonResDto = new CommonResDto(HttpStatus.OK, "channelMember is successfully deleted", null);
        return new ResponseEntity<>(commonResDto, HttpStatus.OK);
    }

    @Operation(summary= "채널 회원 탈퇴")
    @DeleteMapping("/channel/{channelId}/member/leave")
    public ResponseEntity<Object> channelLeave(@PathVariable Long channelId, @AuthenticationPrincipal CustomPrincipal customPrincipal) {
        channelMemberService.channelMemberLeave(channelId, customPrincipal.getEmail());
        CommonResDto commonResDto = new CommonResDto(HttpStatus.OK, "member is successfully deleted", null);
        return new ResponseEntity<>(commonResDto, HttpStatus.OK);
    }
}



