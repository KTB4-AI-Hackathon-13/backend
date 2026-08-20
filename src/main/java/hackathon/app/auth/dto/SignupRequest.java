package hackathon.app.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(@Email @NotBlank String email, @NotBlank String password,
        @NotBlank String passwordConfirmation, @NotBlank @Size(max = 50) String nickname) {}
