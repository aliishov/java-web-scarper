package org.raul.javawebscarper.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.dto.common.BaseResponseDTO;
import org.raul.javawebscarper.dto.common.PageResponseDTO;
import org.raul.javawebscarper.dto.request.admin.CreateAdminRequestDTO;
import org.raul.javawebscarper.dto.request.admin.UpdateAdminRequestDTO;
import org.raul.javawebscarper.dto.response.admin.AdminResponseDTO;
import org.raul.javawebscarper.service.AdminService;
import org.raul.javawebscarper.util.PageRequestFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admins")
@RequiredArgsConstructor
public class AdminController {
	private final AdminService adminService;

	@GetMapping
	public ResponseEntity<BaseResponseDTO<PageResponseDTO<AdminResponseDTO>>> findAll(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "id") String sortBy,
			@RequestParam(defaultValue = "ASC") String direction
	) {
		return ResponseEntity.ok(BaseResponseDTO.success(
				adminService.findAll(PageRequestFactory.create(page, size, sortBy, direction))));
	}

	@GetMapping("/{id}")
	public ResponseEntity<BaseResponseDTO<AdminResponseDTO>> findById(@PathVariable Long id) {
		return ResponseEntity.ok(BaseResponseDTO.success(adminService.findById(id)));
	}

	@PostMapping
	public ResponseEntity<BaseResponseDTO<AdminResponseDTO>> create(@Valid @RequestBody CreateAdminRequestDTO request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(BaseResponseDTO.success(adminService.create(request), "Admin created successfully"));
	}

	@PutMapping("/{id}")
	public ResponseEntity<BaseResponseDTO<AdminResponseDTO>> update(
			@PathVariable Long id,
			@Valid @RequestBody UpdateAdminRequestDTO request
	) {
		return ResponseEntity.ok(BaseResponseDTO.success(
				adminService.update(id, request), "Admin updated successfully"));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<BaseResponseDTO<Void>> delete(@PathVariable Long id, Authentication authentication) {
		adminService.delete(id, authentication.getName());
		return ResponseEntity.ok(BaseResponseDTO.success("Admin deleted successfully"));
	}
}
