import { ChangeDetectorRef, Component, DestroyRef, EventEmitter, inject, Input, Output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { LucideStar } from '@lucide/angular';
import { RatingService, RatingResponse } from '../../services/rating.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { TranslationKey } from '../../i18n/language.js';
import { AuthService } from '../../services/auth.js';

@Component({
  selector: 'app-rating-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, LucideStar, TranslatePipe],
  templateUrl: './rating-form.html',
  host: { class: 'block' },
})
export class RatingFormComponent {
  @Input() jobId!: string;
  @Input() rateeId!: string;
  @Input() jobTitle = '';
  @Input() recipientName = '';
  @Output() busyChange = new EventEmitter<boolean>();
  @Output() reloadRequested = new EventEmitter<void>();
  readonly i18n = inject(I18nService);
  readonly scoreLabels: TranslationKey[] = ['reviews.select', 'reviews.poor', 'reviews.fair', 'reviews.okay', 'reviews.good', 'reviews.excellent'];
  duplicate = false;
  @Output() submitted = new EventEmitter<RatingResponse>();
  @Output() cancelled = new EventEmitter<void>();

  form: FormGroup;
  submitting = false;
  error: string | null = null;
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  hoverScore = 0;
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
  }

  onStarHover(score: number): void {
    this.hoverScore = score;
  }

  onStarLeave(): void {
    this.hoverScore = 0;
  }

  onArrowKey(event: KeyboardEvent): void {
    if (this.submitting) return;
    const current = this.form.value.score || 1;
    const forward = this.i18n.direction() === 'rtl' ? 'ArrowLeft' : 'ArrowRight';
    const backward = this.i18n.direction() === 'rtl' ? 'ArrowRight' : 'ArrowLeft';
    let score: number;
    switch (event.key) {
      case forward: case 'ArrowUp': score = Math.min(5, current + 1); break;
      case backward: case 'ArrowDown': score = Math.max(1, current - 1); break;
      case 'Home': score = 1; break;
      case 'End': score = 5; break;
      default: return;
    }
    event.preventDefault();
    this.selectScore(score);
    (event.currentTarget as HTMLElement).querySelectorAll<HTMLButtonElement>('button')[score - 1]?.focus();
  }

  onSubmit(): void {
    if (this.submitting) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting = true;
    this.busyChange.emit(true);
    this.duplicate = false;
    this.error = null;
    const raterId = this.auth.currentUserId();
    if (!raterId) {
      this.submitting = false;
      this.busyChange.emit(false);
      this.error = this.i18n.translate('reviews.signIn');
      return;
    }

    this.ratingService
      .createRating({
        jobId: this.jobId,
        rateeId: this.rateeId,
        score: this.form.value.score,
        comment: this.form.value.comment?.trim() || undefined,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.submitting = false;
          this.busyChange.emit(false);
          this.form.reset({ score: 0, comment: '' });
          this.submitted.emit(response);
          this.changeDetector.markForCheck();
        },
        error: (error) => {
          this.submitting = false;
          this.busyChange.emit(false);
          this.duplicate = error.error?.error?.code === 'DUPLICATE_RATING';
          this.error = this.i18n.translate(this.duplicate ? 'reviews.duplicate' : 'reviews.submitError');
          this.changeDetector.markForCheck();
        },
      });
  }

  onCancel(): void {
    if (this.submitting) return;
    this.cancelled.emit();
  }
}
