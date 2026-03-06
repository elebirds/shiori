package moe.hhm.shiori.user.address.service;

import java.util.List;
import moe.hhm.shiori.common.error.UserErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.user.address.dto.UserAddressResponse;
import moe.hhm.shiori.user.address.dto.UserAddressUpsertRequest;
import moe.hhm.shiori.user.address.model.UserAddressEntity;
import moe.hhm.shiori.user.address.model.UserAddressRecord;
import moe.hhm.shiori.user.address.repository.UserAddressMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserAddressService {

    private final UserAddressMapper userAddressMapper;

    public UserAddressService(UserAddressMapper userAddressMapper) {
        this.userAddressMapper = userAddressMapper;
    }

    public List<UserAddressResponse> listMyAddresses(Long userId) {
        return userAddressMapper.listByUserId(userId).stream().map(this::toResponse).toList();
    }

    public UserAddressResponse getMyAddress(Long userId, Long addressId) {
        UserAddressRecord record = requireAddress(userId, addressId);
        return toResponse(record);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserAddressResponse createMyAddress(Long userId, UserAddressUpsertRequest request) {
        validateRequest(request);
        boolean hasAddress = userAddressMapper.countByUserId(userId) > 0;
        boolean targetDefault = Boolean.TRUE.equals(request.isDefault()) || !hasAddress;

        UserAddressEntity entity = new UserAddressEntity();
        entity.setUserId(userId);
        entity.setReceiverName(requireTrimmed(request.receiverName()));
        entity.setReceiverPhone(requireTrimmed(request.receiverPhone()));
        entity.setProvince(requireTrimmed(request.province()));
        entity.setCity(requireTrimmed(request.city()));
        entity.setDistrict(requireTrimmed(request.district()));
        entity.setDetailAddress(requireTrimmed(request.detailAddress()));
        entity.setIsDefault(0);
        userAddressMapper.insert(entity);
        if (targetDefault || userAddressMapper.countDefaultByUserId(userId) <= 0) {
            replaceDefault(userId, entity.getId());
        }
        return getMyAddress(userId, entity.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public UserAddressResponse updateMyAddress(Long userId, Long addressId, UserAddressUpsertRequest request) {
        validateRequest(request);
        UserAddressRecord existing = requireAddress(userId, addressId);

        boolean targetDefault = request.isDefault() == null
                ? isDefault(existing)
                : request.isDefault();
        if (!targetDefault && isDefault(existing) && userAddressMapper.countByUserId(userId) <= 1) {
            targetDefault = true;
        }
        UserAddressEntity entity = new UserAddressEntity();
        entity.setId(addressId);
        entity.setUserId(userId);
        entity.setReceiverName(requireTrimmed(request.receiverName()));
        entity.setReceiverPhone(requireTrimmed(request.receiverPhone()));
        entity.setProvince(requireTrimmed(request.province()));
        entity.setCity(requireTrimmed(request.city()));
        entity.setDistrict(requireTrimmed(request.district()));
        entity.setDetailAddress(requireTrimmed(request.detailAddress()));
        entity.setIsDefault(0);

        if (userAddressMapper.update(entity) == 0) {
            throw new BizException(UserErrorCode.ADDRESS_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        if (targetDefault) {
            replaceDefault(userId, addressId);
        } else if (isDefault(existing)) {
            UserAddressRecord fallback = userAddressMapper.findLatestByUserIdExcluding(userId, addressId);
            if (fallback != null) {
                replaceDefault(userId, fallback.id());
            }
        }
        return getMyAddress(userId, addressId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteMyAddress(Long userId, Long addressId) {
        UserAddressRecord existing = requireAddress(userId, addressId);
        if (userAddressMapper.softDeleteByIdAndUserId(addressId, userId) == 0) {
            throw new BizException(UserErrorCode.ADDRESS_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        if (isDefault(existing)) {
            UserAddressRecord fallback = userAddressMapper.findLatestByUserId(userId);
            if (fallback != null) {
                replaceDefault(userId, fallback.id());
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public UserAddressResponse setDefault(Long userId, Long addressId) {
        requireAddress(userId, addressId);
        replaceDefault(userId, addressId);
        return getMyAddress(userId, addressId);
    }

    private void replaceDefault(Long userId, Long addressId) {
        try {
            int affected = userAddressMapper.replaceDefaultByAddressIdAndUserId(addressId, userId);
            if (affected <= 0) {
                throw new BizException(UserErrorCode.ADDRESS_NOT_FOUND, HttpStatus.NOT_FOUND);
            }
        } catch (DuplicateKeyException ex) {
            throw new BizException(UserErrorCode.ADDRESS_INVALID, HttpStatus.CONFLICT, "默认地址更新冲突，请重试");
        }
    }

    private UserAddressRecord requireAddress(Long userId, Long addressId) {
        if (userId == null || userId <= 0 || addressId == null || addressId <= 0) {
            throw new BizException(UserErrorCode.ADDRESS_INVALID, HttpStatus.BAD_REQUEST);
        }
        UserAddressRecord record = userAddressMapper.findByIdAndUserId(addressId, userId);
        if (record == null) {
            throw new BizException(UserErrorCode.ADDRESS_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return record;
    }

    private void validateRequest(UserAddressUpsertRequest request) {
        if (request == null) {
            throw new BizException(UserErrorCode.ADDRESS_INVALID, HttpStatus.BAD_REQUEST);
        }
        requireTrimmed(request.receiverName());
        requireTrimmed(request.receiverPhone());
        requireTrimmed(request.province());
        requireTrimmed(request.city());
        requireTrimmed(request.district());
        requireTrimmed(request.detailAddress());
    }

    private String requireTrimmed(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(UserErrorCode.ADDRESS_INVALID, HttpStatus.BAD_REQUEST);
        }
        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            throw new BizException(UserErrorCode.ADDRESS_INVALID, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private boolean isDefault(UserAddressRecord record) {
        return record != null && record.isDefault() != null && record.isDefault() == 1;
    }

    private UserAddressResponse toResponse(UserAddressRecord record) {
        return new UserAddressResponse(
                record.id(),
                record.userId(),
                record.receiverName(),
                record.receiverPhone(),
                record.province(),
                record.city(),
                record.district(),
                record.detailAddress(),
                isDefault(record),
                record.createdAt(),
                record.updatedAt()
        );
    }
}
