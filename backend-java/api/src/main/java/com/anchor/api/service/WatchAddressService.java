package com.anchor.api.service;

import com.anchor.common.domain.AddressTagEnum;
import com.anchor.common.dto.WatchAddressDTO;

import java.util.List;

public interface WatchAddressService {

    void reloadAll();

    List<WatchAddressDTO> list(long chainId);

    WatchAddressDTO create(long chainId, String address, String name, AddressTagEnum tag, String note);

    WatchAddressDTO update(long id, long chainId, String address, String name, AddressTagEnum tag, String note);

    void delete(long id);
}
