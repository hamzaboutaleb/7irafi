import { HttpErrorResponse, HttpEventType } from '@angular/common/http';
import { Component, DestroyRef, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { UserService } from '../../services/user.js';
import { ApiError } from '../../types/api.js';
import { UserProfile } from '../../types/user.js';

const ACCEPTED_TYPES = ['image/png', 'image/jpeg', 'image/webp'];
const MAX_BYTES = 2 * 1024 * 1024;

/**
 * Picking, previewing, uploading and removing the account's picture.
 *
 * The checks here are for the person's benefit — instant feedback instead of a
 * round trip. The server repeats every one of them, because anything sent from
 * a browser can be forged.
 */
@Component({
  selector: 'app-avatar-upload',
  imports: [],
  templateUrl: './avatar-upload.html',
})
export class AvatarUpload {
  private readonly userService = inject(UserService);
  private readonly destroyRef = inject(DestroyRef);

  readonly profile = input.required<UserProfile>();
  readonly profileChange = output<UserProfile>();

  readonly accept = ACCEPTED_TYPES.join(',');
  readonly maxMegabytes = MAX_BYTES / (1024 * 1024);

  /** Local object URL of the picked file, shown before it is uploaded. */
  readonly preview = signal<string | null>(null);
  readonly uploading = signal(false);
  readonly progress = signal(0);
  readonly removing = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);

  private file: File | null = null;

  get initials(): string {
    const profile = this.profile();
    return `${profile.firstName.charAt(0)}${profile.lastName.charAt(0)}`.toUpperCase();
  }

  pick(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.reset();

    if (!file) return;

    if (!ACCEPTED_TYPES.includes(file.type)) {
      this.error.set('Choose a PNG, JPEG or WebP image.');
      input.value = '';
      return;
    }

    if (file.size > MAX_BYTES) {
      this.error.set(`That image is ${(file.size / 1024 / 1024).toFixed(1)} MB. The limit is ${this.maxMegabytes} MB.`);
      input.value = '';
      return;
    }

    this.file = file;
    this.preview.set(URL.createObjectURL(file));
  }

  upload(): void {
    if (!this.file || this.uploading()) return;

    this.uploading.set(true);
    this.progress.set(0);
    this.error.set(null);

    this.userService.uploadAvatar(this.file).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (event) => {
        if (event.type === HttpEventType.UploadProgress && event.total) {
          this.progress.set(Math.round((event.loaded / event.total) * 100));
        }

        if (event.type === HttpEventType.Response && event.body) {
          this.profileChange.emit(event.body.data);
          this.clearPick();
          this.uploading.set(false);
          this.notice.set('Picture updated.');
        }
      },
      error: (error: HttpErrorResponse) => {
        const body = error.error as ApiError | undefined;
        this.uploading.set(false);
        this.error.set(body?.error?.message ?? 'Unable to upload that image. Please try again.');
      },
    });
  }

  remove(): void {
    if (this.removing()) return;

    this.removing.set(true);
    this.error.set(null);

    this.userService.deleteAvatar().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.removing.set(false);
        this.notice.set('Picture removed.');
        this.profileChange.emit({ ...this.profile(), avatarUrl: null });
      },
      error: (error: HttpErrorResponse) => {
        const body = error.error as ApiError | undefined;
        this.removing.set(false);
        this.error.set(body?.error?.message ?? 'Unable to remove the picture.');
      },
    });
  }

  cancel(): void {
    this.clearPick();
  }

  private clearPick(): void {
    const preview = this.preview();
    if (preview) URL.revokeObjectURL(preview);

    this.file = null;
    this.preview.set(null);
    this.progress.set(0);
  }

  private reset(): void {
    this.error.set(null);
    this.notice.set(null);
    this.clearPick();
  }
}
