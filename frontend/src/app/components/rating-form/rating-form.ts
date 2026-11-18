import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LucideStar } from '@lucide/angular';

import { TranslationKey } from '../../i18n/language.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { AuthService } from '../../services/auth.js';
import { I18nService } from '../../services/i18n.js';
import { CreateRatingRequest, RatingResponse, RatingService } from '../../services/rating.js';

/**
 * Collects a score and optional comment, then publishes a review.
 * The parent supplies the job and recipient and reacts to submission/cancel events.
 * Failed requests keep the user's values so they can retry.
 */
@Component({
  selector: 'app-rating-form',
  imports: [ReactiveFormsModule, LucideStar, TranslatePipe],
  templateUrl: './rating-form.html',
  styleUrl: './rating-form.css',
})
export class RatingFormComponent {
  readonly jobId = input.required<string>();
  readonly reviewedUserId = input.required<string>();
  readonly jobTitle = input('');
  readonly recipientName = input('');

  readonly submittingChange = output<boolean>();
  readonly submitted = output<RatingResponse>();
  readonly reloadRequested = output<void>();
  readonly cancelled = output<void>();

  private readonly formBuilder = inject(FormBuilder);
  private readonly ratingService = inject(RatingService);
  private readonly authService = inject(AuthService);
  private readonly i18n = inject(I18nService);
  private readonly destroyRef = inject(DestroyRef);

  // Controls always contain a number and a string, including after a reset.
  readonly form = this.formBuilder.nonNullable.group({
    score: [0, [Validators.required, Validators.min(1), Validators.max(5)]],
    comment: ['', [Validators.maxLength(1000)]],
  });

  readonly stars = [1, 2, 3, 4, 5];

  // Index 0 means no selection. Indexes 1–5 describe the corresponding star score.
  readonly scoreLabelKeys: TranslationKey[] = [
    'reviews.select',
    'reviews.poor',
    'reviews.fair',
    'reviews.okay',
    'reviews.good',
    'reviews.excellent',
  ];

  readonly hoveredScore = signal(0);
  readonly isSubmitting = signal(false);
  readonly hasExistingReview = signal(false);
  readonly submissionError = signal<string | null>(null);

  selectedScore(): number {
    return this.form.controls.score.value;
  }

  /** Hover changes the preview, without changing the score that will be published. */
  displayedScore(): number {
    return this.hoveredScore() || this.selectedScore();
  }

  selectScore(score: number): void {
    if (this.isSubmitting()) return;

    this.form.controls.score.setValue(score);
    this.clearScorePreview();
  }

  previewScore(score: number): void {
    if (this.isSubmitting()) return;

    this.hoveredScore.set(score);
  }

  clearScorePreview(): void {
    this.hoveredScore.set(0);
  }

  /** Only score-navigation keys are handled; Tab and Enter keep their normal behavior. */
  handleScoreKeydown(event: KeyboardEvent, scorePicker: HTMLElement): void {
    if (this.isSubmitting()) return;

    const nextScore = this.getKeyboardScore(event.key);
    if (nextScore === null) return;

    event.preventDefault();
    this.selectScore(nextScore);

    const scoreButtons = scorePicker.querySelectorAll<HTMLButtonElement>('button');
    const selectedButton = scoreButtons[nextScore - 1];
    selectedButton?.focus();
  }

  submitReview(): void {
    if (this.isSubmitting() || this.hasExistingReview()) return;

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    if (!this.authService.currentUserId()) {
      this.submissionError.set(this.i18n.translate('reviews.signIn'));
      return;
    }

    const formValues = this.form.getRawValue();
    const trimmedComment = formValues.comment.trim();
    const reviewRequest: CreateRatingRequest = {
      jobId: this.jobId(),
      rateeId: this.reviewedUserId(),
      score: formValues.score,
      comment: trimmedComment || undefined,
    };

    this.submissionError.set(null);
    this.setSubmitting(true);

    this.ratingService
      .publishReview(reviewRequest)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (publishedReview) => {
          this.setSubmitting(false);
          this.form.reset();
          this.submitted.emit(publishedReview);
        },
        error: (error: HttpErrorResponse) => {
          this.setSubmitting(false);
          this.hasExistingReview.set(error.error?.error?.code === 'DUPLICATE_RATING');

          const errorKey = this.hasExistingReview() ? 'reviews.duplicate' : 'reviews.submitError';
          this.submissionError.set(this.i18n.translate(errorKey));
        },
      });
  }

  cancelReview(): void {
    if (this.isSubmitting()) return;

    this.cancelled.emit();
  }

  private setSubmitting(isSubmitting: boolean): void {
    this.isSubmitting.set(isSubmitting);
    this.submittingChange.emit(isSubmitting);
  }

  /** Horizontal arrows follow reading direction; vertical arrows, Home, and End do not. */
  private getKeyboardScore(key: string): number | null {
    const currentScore = this.selectedScore() || 1;
    const isRightToLeft = this.i18n.direction() === 'rtl';
    const increaseKey = isRightToLeft ? 'ArrowLeft' : 'ArrowRight';
    const decreaseKey = isRightToLeft ? 'ArrowRight' : 'ArrowLeft';

    switch (key) {
      case increaseKey:
      case 'ArrowUp':
        return Math.min(5, currentScore + 1);
      case decreaseKey:
      case 'ArrowDown':
        return Math.max(1, currentScore - 1);
      case 'Home':
        return 1;
      case 'End':
        return 5;
      default:
        return null;
    }
  }
}
