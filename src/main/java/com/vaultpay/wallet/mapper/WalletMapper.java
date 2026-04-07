package com.vaultpay.wallet.mapper;

import com.vaultpay.wallet.domain.Wallet;
import com.vaultpay.wallet.dto.WalletDtos;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * MapStruct mapper for the Wallet domain.
 *
 * The Wallet entity has a nested User object (the owner).
 * We only want to expose the userId in the response — not the full User object.
 *
 * @Mapping(source = "user.id", target = "userId") tells MapStruct to:
 *   navigate into the nested user object → get its id → map it to userId in the DTO.
 *
 * MapStruct resolves this automatically at compile time.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface WalletMapper {

    @Mapping(source = "user.id",  target = "userId")
    @Mapping(source = "balance",  target = "balanceInCents")
    @Mapping(source = "createdAt", target = "createdAt")
    WalletDtos.WalletResponse toWalletResponse(Wallet wallet);
}