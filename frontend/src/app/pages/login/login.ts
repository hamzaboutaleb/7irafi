import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LucideDynamicIcon, LucideArrowRight } from '@lucide/angular';
import { AuthService } from '../../services/auth.js';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { ApiError } from '../../types/api.js';

@Component({
  selector: 'app-login',
  imports: [LucideDynamicIcon, ReactiveFormsModule, CommonModule, RouterLink, TranslatePipe],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  arrowIcon = LucideArrowRight;
  private fb = inject(FormBuilder);
  private router = inject(Router);
  private authService = inject(AuthService);
  private destroyRef = inject(DestroyRef);

  errorMessage = signal<string | null>(null);

  form = this.fb.group(
    {
      email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
      password: ['', [Validators.required, Validators.maxLength(128)]],
    },
  );

  login() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.errorMessage.set(null);
    this.authService
      .login(this.form.value.email!, this.form.value.password!)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.authService.saveToken(response.accessToken);
          this.router.navigate(['/']);
        },
        error: (err: HttpErrorResponse) => {
          const body = err.error as ApiError | undefined;
          this.errorMessage.set(body?.error?.message ?? 'Login failed. Please try again.');
        },
      });
  }

  get email() {
    return this.form.get('email')!;
  }

  get password() {
    return this.form.get('password')!;
  }
}
