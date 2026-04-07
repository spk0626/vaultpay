package com.vaultpay.transaction.mapper;

import com.vaultpay.transaction.domain.Transaction;
import com.vaultpay.transaction.dto.TransactionDtos;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * MapStruct mapper for the Transaction domain.
 *
 * Transaction → TransactionResponse field mappings:
 *   transaction.wallet.id   → walletId       (navigate into nested Wallet entity)
 *   transaction.amount      → amountInCents  (renamed for API clarity)
 *
 * MapStruct generates the implementation at compile time. If you misspell a field
 * name, the build fails — not a runtime NullPointerException. This is the safety
 * advantage over BeanUtils.copyProperties().
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TransactionMapper {

    @Mapping(source = "wallet.id",  target = "walletId")
    @Mapping(source = "amount",     target = "amountInCents")
    @Mapping(source = "createdAt",  target = "createdAt")
    TransactionDtos.TransactionResponse toResponse(Transaction transaction);
}