package moe.hhm.shiori.user.address.controller;

import jakarta.validation.Valid;
import java.util.List;
import moe.hhm.shiori.user.address.dto.UserAddressResponse;
import moe.hhm.shiori.user.address.dto.UserAddressUpsertRequest;
import moe.hhm.shiori.user.address.service.UserAddressService;
import moe.hhm.shiori.user.security.CurrentUserSupport;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@Validated
@RestController
@RequestMapping("/api/user/me/addresses")
public class UserAddressController {

    private final UserAddressService userAddressService;

    public UserAddressController(UserAddressService userAddressService) {
        this.userAddressService = userAddressService;
    }

    @GetMapping
    public List<UserAddressResponse> list(Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return userAddressService.listMyAddresses(userId);
    }

    @GetMapping("/{addressId}")
    public UserAddressResponse detail(@PathVariable Long addressId, Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return userAddressService.getMyAddress(userId, addressId);
    }

    @PostMapping
    public UserAddressResponse create(@Valid @RequestBody UserAddressUpsertRequest request,
                                      Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return userAddressService.createMyAddress(userId, request);
    }

    @PutMapping("/{addressId}")
    public UserAddressResponse update(@PathVariable Long addressId,
                                      @Valid @RequestBody UserAddressUpsertRequest request,
                                      Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return userAddressService.updateMyAddress(userId, addressId, request);
    }

    @DeleteMapping("/{addressId}")
    public void delete(@PathVariable Long addressId, Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        userAddressService.deleteMyAddress(userId, addressId);
    }

    @PostMapping("/{addressId}/default")
    public UserAddressResponse setDefault(@PathVariable Long addressId, Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return userAddressService.setDefault(userId, addressId);
    }
}
