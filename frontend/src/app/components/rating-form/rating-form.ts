import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { LucideAngularModule, Star } from '@lucide/angular';
import { RatingService, RatingResponse } from '../../services/rating.js';
import { AuthService } from '../../services/auth.js';

@Component({
  selector: 'app-rating-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, LucideAngularModule],
  templateUrl: './rating-form.html',
  styleUrl: './rating-form.css',
})
export class RatingFormComponent implements OnInit {
  @Input() jobId!: string;
  @Input() rateeId!: string;
  @Output() submitted = new EventEmitter<RatingResponse>();
  @Output() cancelled = new EventEmitter<void>();

  form: FormGroup;
  submitting = false;
  hoverScore = 0;
  readonly stars = [1, 2, 3, 4, 5];
  readonly Star = Star;

  constructor(
    private fb: FormBuilder,
    private ratingService: RatingService,
    private auth: AuthService,
  ) {
    this.form = this.fb.group({
      score: [0, [Validators.required, Validators.min(1), Validators.max(5)]],
      comment: ['', [Validators.maxLength(1000)]],
    });
  }

  ngOnInit(): void {}

  selectScore(score: number): void {
    this.form.patchValue({ score });
    this.hoverScore = 0;
  }

  onStarHover(score: number): void {
    this.hoverScore = score;
  }

  onStarLeave(): void {
    this.hoverScore = 0;
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting = true;
    const raterId = this.auth.currentUserId();
    if (!raterId) {
      this.submitting = false;
      return;
    }

    this.ratingService
      .createRating({
        jobId: this.jobId,
        rateeId: this.rateeId,
        score: this.form.value.score,
        comment: this.form.value.comment || undefined,
      })
      .subscribe({
        next: (response) => {
          this.submitting = false;
          this.form.reset({ score: 0, comment: '' });
          this.submitted.emit(response);
        },
        error: (error) => {
          this.submitting = false;
          console.error('Rating submission failed:', error);
        },
      });
  }

  onCancel(): void {
    this.cancelled.emit();
  }
}