import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RatingService, RatingResponse } from '../../services/rating.js';
import { AuthService } from '../../services/auth.js';

@Component({
  selector: 'app-rating-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
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
  }

  onSubmit(): void {
    // TODO: implement
  }

  onCancel(): void {
    this.cancelled.emit();
  }
}