import { ChangeDetectorRef, Component, DestroyRef, EventEmitter, inject, Input, Output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { LucideStar } from '@lucide/angular';
import { RatingService, RatingResponse } from '../../services/rating.js';
import { AuthService } from '../../services/auth.js';

@Component({
  selector: 'app-rating-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, LucideStar],
  templateUrl: './rating-form.html',
  styleUrl: './rating-form.css',
})
export class RatingFormComponent {
  @Input() jobId!: string;
  @Input() rateeId!: string;
  @Output() submitted = new EventEmitter<RatingResponse>();
  @Output() cancelled = new EventEmitter<void>();

  form: FormGroup;
  submitting = false;
  error: string | null = null;
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  hoverScore = 0;
  focusedStar = 0;
  readonly stars = [1, 2, 3, 4, 5];

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

  selectScore(score: number): void {
    this.form.patchValue({ score });
    this.hoverScore = 0;
    this.focusedStar = score;
  }

  onStarHover(score: number): void {
    this.hoverScore = score;
  }

  onStarLeave(): void {
    this.hoverScore = 0;
  }

  onArrowKey(event: KeyboardEvent): void {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return;
    event.preventDefault();
    const currentScore = this.form.value.score || this.focusedStar || 0;
    let newScore = currentScore;
    
    if (event.key === 'ArrowRight') {
      newScore = Math.min(5, currentScore + 1);
    } else if (event.key === 'ArrowLeft') {
      newScore = Math.max(1, currentScore - 1);
    }
    
    if (newScore !== currentScore) {
      event.preventDefault();
      this.selectScore(newScore);
      const group = event.currentTarget as HTMLElement;
      group.querySelectorAll<HTMLButtonElement>('button')[newScore - 1]?.focus();
    }
  }

  onStarFocus(score: number): void {
    this.focusedStar = score;
  }

  onStarBlur(): void {
    this.focusedStar = this.form.value.score || 0;
  }

  onSubmit(): void {
    if (this.submitting) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting = true;
    this.error = null;
    const raterId = this.auth.currentUserId();
    if (!raterId) {
      this.submitting = false;
      this.error = 'Please sign in to submit a rating.';
      return;
    }

    this.ratingService
      .createRating({
        jobId: this.jobId,
        rateeId: this.rateeId,
        score: this.form.value.score,
        comment: this.form.value.comment || undefined,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.submitting = false;
          this.form.reset({ score: 0, comment: '' });
          this.focusedStar = 0;
          this.submitted.emit(response);
          this.changeDetector.markForCheck();
        },
        error: (error) => {
          this.submitting = false;
          this.error = error.error?.error?.message ?? 'Unable to submit your rating. Please try again.';
          this.changeDetector.markForCheck();
        },
      });
  }

  onCancel(): void {
    if (this.submitting) return;
    this.cancelled.emit();
  }
}
