package com.anchor.api.vo;

import com.anchor.common.domain.AddressTagEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WatchAddressRequest(@NotNull Long chainId, @NotBlank String address, String name,
                                  @NotNull AddressTagEnum tag, String note) {
}
