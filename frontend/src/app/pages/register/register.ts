import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../services/auth.js';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiError } from '../../types/api.js';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: './register.css',
})
export class Register {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);

  form = this.fb.group(
    {
      firstName: ['', [Validators.required, Validators.maxLength(50)]],
      lastName: ['', [Validators.required, Validators.maxLength(50)]],
      email: ['', [Validators.required, Validators.maxLength(50), Validators.email]],
      password: ['', [Validators.required, Validators.maxLength(50), Validators.minLength(3)]],
    },
    { updateOn: 'blur' },
  );

  errorMessage = signal<string | null>(null);

  register() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.errorMessage.set(null);

    const { firstName, lastName, email, password } = this.form.getRawValue();

    this.authService
      .register({
        firstName: firstName!,
        lastName: lastName!,
        email: email!,
        password: password!,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.authService.saveToken(response.accessToken);
          this.router.navigate(['/home']);
        },
        error: (err: HttpErrorResponse) => {
          const body = err.error as ApiError | undefined;
          this.errorMessage.set(
            body?.error?.message ?? 'Registration failed. Please try again.',
          );
        },
      });
  }

  get firstName() {
    return this.form.get('firstName')!;
  }
  get lastName() {
    return this.form.get('lastName')!;
  }
  get email() {
    return this.form.get('email')!;
  }
  get password() {
    return this.form.get('password')!;
  }
}
