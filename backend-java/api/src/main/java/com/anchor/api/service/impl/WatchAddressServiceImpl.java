package com.anchor.api.service.impl;

import com.anchor.api.service.WatchAddressService;
import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.RedisKeys;
import com.anchor.common.dao.WatchAddressMapper;
import com.anchor.common.entity.WatchAddressDO;
import com.anchor.common.domain.AddressTagEnum;
import com.anchor.common.domain.AnchorException;
import com.anchor.common.domain.ChainTypeEnum;
import com.anchor.common.dto.WatchAddressDTO;
import com.anchor.common.support.Addresses;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 落库监控地址，并保证 Redis 集合与数据库一致。
 */
@Service
public class WatchAddressServiceImpl implements WatchAddressService {

    private final WatchAddressMapper watchAddressMapper;
    private final ChainRegistry chainRegistry;
    private final StringRedisTemplate redis;

    public WatchAddressServiceImpl(WatchAddressMapper watchAddressMapper, ChainRegistry chainRegistry, StringRedisTemplate redis) {
        this.watchAddressMapper = watchAddressMapper;
        this.chainRegistry = chainRegistry;
        this.redis = redis;
    }

    @Override
    public void reloadAll() {
        for (ChainDefinition chain : chainRegistry.all()) {
            reload(chain.chainId());
        }
    }

    @Override
    public List<WatchAddressDTO> list(long chainId) {
        chainRegistry.requireById(chainId);
        return watchAddressMapper.listByChain(chainId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WatchAddressDTO create(long chainId, String address, String name, AddressTagEnum tag, String note) {
        WatchAddressDO request = toDataObject(chainId, address, name, tag, note);
        ChainDefinition chain = prepare(request);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        request.setCreatedBy(AnchorConstants.ACTOR_API);
        request.setUpdatedBy(AnchorConstants.ACTOR_API);
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        try {
            watchAddressMapper.insert(request);
        } catch (DataIntegrityViolationException ex) {
            throw new AnchorException("CONFLICT", "该地址已在监控列表中");
        }
        redis.opsForSet().add(RedisKeys.watchSet(chain.chainId()), request.getAddress());
        return requireView(request.getId(), request.getChainId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WatchAddressDTO update(long id, long chainId, String address, String name, AddressTagEnum tag, String note) {
        WatchAddressDO existing = watchAddressMapper.selectById(id);
        if (existing == null) {
            throw new AnchorException("NOT_FOUND", "监控地址不存在");
        }
        WatchAddressDO request = toDataObject(chainId, address, name, tag, note);
        ChainDefinition chain = prepare(request);
        String previousKey = RedisKeys.watchSet(existing.getChainId());
        String previousAddress = existing.getAddress();
        existing.setChainId(request.getChainId());
        existing.setAddress(request.getAddress());
        existing.setName(request.getName());
        existing.setTag(request.getTag());
        existing.setNote(request.getNote());
        existing.setUpdatedBy(AnchorConstants.ACTOR_API);
        existing.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        try {
            watchAddressMapper.updateById(existing);
        } catch (DataIntegrityViolationException ex) {
            throw new AnchorException("CONFLICT", "该地址已在监控列表中");
        }
        redis.opsForSet().remove(previousKey, previousAddress);
        redis.opsForSet().add(RedisKeys.watchSet(chain.chainId()), existing.getAddress());
        return requireView(existing.getId(), existing.getChainId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(long id) {
        WatchAddressDO existing = watchAddressMapper.selectById(id);
        if (existing == null) {
            throw new AnchorException("NOT_FOUND", "监控地址不存在");
        }
        watchAddressMapper.deleteById(id);
        redis.opsForSet().remove(RedisKeys.watchSet(existing.getChainId()), existing.getAddress());
    }

    private WatchAddressDTO requireView(Long id, Long chainId) {
        return list(chainId).stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AnchorException("NOT_FOUND", "监控地址不存在"));
    }

    private static WatchAddressDO toDataObject(long chainId, String address, String name, AddressTagEnum tag, String note) {
        WatchAddressDO data = new WatchAddressDO();
        data.setChainId(chainId);
        data.setAddress(address);
        data.setName(name);
        data.setTag(tag.name());
        data.setNote(note);
        return data;
    }

    private ChainDefinition prepare(WatchAddressDO request) {
        ChainDefinition chain = chainRegistry.requireById(request.getChainId());
        if (chain.type() == ChainTypeEnum.EVM) {
            request.setAddress(Addresses.normalizeEvm(request.getAddress()));
        }
        if (request.getName() != null && request.getName().length() > AnchorConstants.WATCH_NAME_MAX_LENGTH) {
            throw new AnchorException("INVALID_NAME", "名称过长");
        }
        if (request.getNote() != null && request.getNote().length() > AnchorConstants.WATCH_NOTE_MAX_LENGTH) {
            throw new AnchorException("INVALID_NOTE", "备注过长");
        }
        return chain;
    }

    private void reload(long chainId) {
        String key = RedisKeys.watchSet(chainId);
        redis.delete(key);
        List<String> addresses = watchAddressMapper.listAddresses(chainId);
        if (!addresses.isEmpty()) {
            redis.opsForSet().add(key, addresses.toArray(String[]::new));
        }
    }
}
