package cn.cctstudio.smartdiypcassistantbackend.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 64, message = "昵称不能超过 64 字") String nickname,
        @Size(max = 512, message = "头像地址不能超过 512 字") String avatarKey,
        @Size(max = 512, message = "背景图地址不能超过 512 字") String backgroundKey,
        @Size(max = 300, message = "个人简介不能超过 300 字") String bio,
        @Size(max = 16, message = "性别不能超过 16 字") String gender,
        @Size(max = 20, message = "生日不能超过 20 字") String birthday,
        @Size(max = 80, message = "国家或地区不能超过 80 字") String countryOrRegion,
        @Size(max = 80, message = "省份不能超过 80 字") String province,
        @Size(max = 80, message = "城市不能超过 80 字") String city,
        Boolean profilePublic,
        Boolean spacePublic
) {
}
