package com.company.mailing.repository;

import com.company.mailing.entity.UserMailSettings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMailSettingsRepository extends JpaRepository<UserMailSettings, UUID> {
    Optional<UserMailSettings> findByOwnerUserId(UUID ownerUserId);
}
