package com.vaultpay.user.mapper;

import com.vaultpay.user.domain.User;
import com.vaultpay.user.dto.UserDtos;
import org.mapstruct.Mapper;  // MapStruct annotation to indicate this interface is a mapper. MapStruct will generate an implementation of this interface at compile time.
import org.mapstruct.Mapping;   // MapStruct annotation to specify how to map fields when source and target field names differ.
import org.mapstruct.MappingConstants;  // MapStruct constants for componentModel values (e.g., SPRING, CDI, JSR330). Using the constant improves readability and reduces typos compared to a raw string "spring".

// mapstruct is a code generator that creates mapping code between Java objects. It helps to convert one type of object to another, such as converting a User entity to a UserResponse DTO. By defining an interface with mapping methods and using annotations, MapStruct generates the implementation at compile time, reducing boilerplate code and improving maintainability.

/**
 * MapStruct mapper — generates the implementation at COMPILE TIME.
 *
 * When you run 'mvn compile', MapStruct reads this interface and generates a
 * class UserMapperImpl.java in target/generated-sources/. You can inspect it.
 *
 * componentModel = "spring" → the generated class is annotated with @Component
 * so Spring manages it as a bean we can @Autowired or inject via constructor.
 *
 * @Mapping tells MapStruct how to handle fields when names differ between source and target.
 * Here: User.email → UserResponse.email (same name, so @Mapping isn't technically needed,
 * but it's shown for clarity).
 *
 * IMPORTANT: The password field is intentionally NOT mapped to any response DTO.
 * MapStruct silently ignores unmapped target fields, so the password is never exposed.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    @Mapping(source = "createdAt", target = "createdAt")   
    UserDtos.UserResponse toUserResponse(User user);
}