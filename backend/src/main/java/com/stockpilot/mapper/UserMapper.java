package com.stockpilot.mapper;

import com.stockpilot.dto.UserDtos;
import com.stockpilot.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    /** Never exposes the password hash or the optimistic-lock version. */
    public static UserDtos.Response toResponse(User user) {
        return new UserDtos.Response(user.getId(), user.getEmail(), user.getFullName(), user.roleName(),
                user.isEnabled(), user.getLastLoginAt(), user.getCreatedAt());
    }

    public static String displayName(User user) {
        return user == null ? null : user.getFullName();
    }
}
