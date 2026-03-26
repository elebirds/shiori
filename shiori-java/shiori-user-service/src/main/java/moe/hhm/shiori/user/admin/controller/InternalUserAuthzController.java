package moe.hhm.shiori.user.admin.controller;

import jakarta.validation.Valid;
import java.util.List;
import moe.hhm.shiori.common.mvc.SkipResultWrap;
import moe.hhm.shiori.user.authz.dto.AuthzSnapshotBatchRequest;
import moe.hhm.shiori.user.authz.dto.AuthzSnapshotBatchResponse;
import moe.hhm.shiori.user.authz.dto.AuthzSnapshotResponse;
import moe.hhm.shiori.user.authz.service.AuthzSnapshotService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@SkipResultWrap
@RestController
@RequestMapping("/internal/authz/users")
public class InternalUserAuthzController {

    private final AuthzSnapshotService authzSnapshotService;

    public InternalUserAuthzController(AuthzSnapshotService authzSnapshotService) {
        this.authzSnapshotService = authzSnapshotService;
    }

    @GetMapping("/{userId}/snapshot")
    public AuthzSnapshotResponse getSnapshot(@PathVariable Long userId) {
        return authzSnapshotService.getSnapshot(userId);
    }

    @PostMapping("/snapshot/batch")
    public AuthzSnapshotBatchResponse getSnapshotBatch(@Valid @RequestBody AuthzSnapshotBatchRequest request) {
        List<AuthzSnapshotResponse> snapshots = authzSnapshotService.getSnapshotBatch(request.userIds());
        return new AuthzSnapshotBatchResponse(snapshots);
    }
}
