package com.verinite.auth_service.controller;


import com.verinite.auth_service.dto.CreateUserRequest;
import com.verinite.auth_service.dto.UserDto;
import com.verinite.auth_service.service.UserService;
import com.verinite.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserDto>>
    createUser(
            @RequestBody
            @Valid
            CreateUserRequest request) {

        UserDto user =
                userService.createUser(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        user,
                        "User created successfully"));
    }

    @GetMapping
    public ResponseEntity<
            ApiResponse<List<UserDto>>>
    getUsers() {

        return ResponseEntity.ok(
                ApiResponse.success(
                        userService.getAllUsers(),
                        "Users fetched successfully"
                )
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<
            ApiResponse<Void>>
    deleteUser(
            @PathVariable Long id) {

        userService.deleteUser(id);

        return ResponseEntity.ok(
                ApiResponse.success(
                        null,
                        "User deleted successfully"
                )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDto>> getUserById(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        userService.getUserById(id),
                        "User fetched successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDto>> updateUser(
            @PathVariable Long id,
            @RequestBody @Valid CreateUserRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        userService.updateUser(id, request),
                        "User updated successfully"));
    }
}
