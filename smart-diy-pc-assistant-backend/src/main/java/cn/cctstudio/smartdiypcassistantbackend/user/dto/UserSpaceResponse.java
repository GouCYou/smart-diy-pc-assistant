package cn.cctstudio.smartdiypcassistantbackend.user.dto;

import cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto.BuildConfigResponse;
import cn.cctstudio.smartdiypcassistantbackend.community.dto.CommunityPostResponse;
import cn.cctstudio.smartdiypcassistantbackend.news.dto.NewsResponse;

import java.util.List;

public record UserSpaceResponse(
        Long userId,
        String username,
        String nickname,
        String avatarUrl,
        String backgroundUrl,
        String bio,
        String gender,
        String birthday,
        String countryOrRegion,
        String province,
        String city,
        boolean profilePublic,
        boolean spacePublic,
        boolean mine,
        boolean following,
        long followingCount,
        long followerCount,
        List<CommunityPostResponse> posts,
        List<BuildConfigResponse> favoriteBuilds,
        List<CommunityPostResponse> favoritePosts,
        List<NewsResponse> favoriteNews
) {
}
