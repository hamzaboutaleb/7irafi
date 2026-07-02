import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: './register.css',
})
export class Register {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  form = this.fb.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  loading = signal(false);
  error = signal('');

  submit() {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.error.set('');

    const { firstName, lastName, email, password } = this.form.value;

    this.auth
      .register({ firstName: firstName!, lastName: lastName!, email: email!, password: password! })
      .subscribe({
        next: () => this.router.navigateByUrl('/login'),
        error: (err) => {
          this.error.set(err.error?.error?.message ?? 'Registration failed');
          this.loading.set(false);
        },
      });
  }
}
