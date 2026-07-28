package org.raul.javawebscarper.service;

import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.dto.common.PageResponseDTO;
import org.raul.javawebscarper.dto.request.admin.CreateAdminRequestDTO;
import org.raul.javawebscarper.dto.request.admin.UpdateAdminRequestDTO;
import org.raul.javawebscarper.dto.response.admin.AdminResponseDTO;
import org.raul.javawebscarper.exception.BadRequestException;
import org.raul.javawebscarper.exception.DuplicateResourceException;
import org.raul.javawebscarper.exception.ForbiddenException;
import org.raul.javawebscarper.exception.ResourceNotFoundException;
import org.raul.javawebscarper.mapper.AdminMapper;
import org.raul.javawebscarper.model.Admin;
import org.raul.javawebscarper.model.enumerated.AdminRole;
import org.raul.javawebscarper.repository.AdminRepository;
import org.raul.javawebscarper.repository.RefreshTokenRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminService {
	private final AdminRepository adminRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional(readOnly = true)
	public PageResponseDTO<AdminResponseDTO> findAll(Pageable pageable) {
		return PageResponseDTO.from(adminRepository.findAll(pageable), AdminMapper::toResponse);
	}

	@Transactional(readOnly = true)
	public AdminResponseDTO findById(Long id) {
		return AdminMapper.toResponse(getEntity(id));
	}

	@Transactional
	public AdminResponseDTO create(CreateAdminRequestDTO request) {
		String username = normalize(request.username());
		ensureUsernameAvailable(username, null);
		Admin admin = Admin.builder()
				.username(username)
				.passwordHash(passwordEncoder.encode(request.password()))
				.role(request.role())
				.build();
		return AdminMapper.toResponse(adminRepository.save(admin));
	}

	@Transactional
	public AdminResponseDTO update(Long id, UpdateAdminRequestDTO request) {
		if (request.username() == null && request.password() == null && request.role() == null) {
			throw new BadRequestException("At least one of username, password or role must be provided");
		}
		Admin admin = getEntity(id);
		String originalUsername = admin.getUsername();
		if (request.username() != null) {
			String username = normalize(request.username());
			ensureUsernameAvailable(username, id);
			admin.setUsername(username);
		}
		if (request.password() != null) {
			admin.setPasswordHash(passwordEncoder.encode(request.password()));
		}
		if (request.role() != null && request.role() != admin.getRole()) {
			if (admin.getRole() == AdminRole.SUPER_ADMIN
					&& adminRepository.countByRole(AdminRole.SUPER_ADMIN) <= 1) {
				throw new BadRequestException("The last super admin cannot be demoted");
			}
			admin.setRole(request.role());
		}
		if (request.password() != null || request.role() != null
				|| !originalUsername.equals(admin.getUsername())) {
			refreshTokenRepository.deleteByAdmin(admin);
		}
		return AdminMapper.toResponse(admin);
	}

	@Transactional
	public void delete(Long id, String actorUsername) {
		Admin admin = getEntity(id);
		if (admin.getUsername().equalsIgnoreCase(actorUsername)) {
			throw new ForbiddenException("You cannot delete your own account");
		}
		if (admin.getRole() == AdminRole.SUPER_ADMIN
				&& adminRepository.countByRole(AdminRole.SUPER_ADMIN) <= 1) {
			throw new BadRequestException("The last super admin cannot be deleted");
		}
		adminRepository.delete(admin);
	}

	private Admin getEntity(Long id) {
		return adminRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Admin with id '%s' was not found".formatted(id)));
	}

	private void ensureUsernameAvailable(String username, Long excludedId) {
		boolean exists = excludedId == null
				? adminRepository.existsByUsernameIgnoreCase(username)
				: adminRepository.existsByUsernameIgnoreCaseAndIdNot(username, excludedId);
		if (exists) throw new DuplicateResourceException("Admin username '%s' already exists".formatted(username));
	}

	private String normalize(String username) {
		return username.trim().toLowerCase(Locale.ROOT);
	}
}
