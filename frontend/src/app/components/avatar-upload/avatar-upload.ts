import { HttpErrorResponse, HttpEventType } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, Injector, afterNextRender, inject, input, output, signal, viewChild } from '@angular/core';
import { LucideCamera } from '@lucide/angular';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
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
  imports: [TranslatePipe, LucideCamera],
  templateUrl: './avatar-upload.html',
  styleUrls: [
    './avatar-upload.css',
    './avatar-upload-editor.css',
  ],
})
export class AvatarUpload {
  private readonly userService = inject(UserService);
  private readonly i18n = inject(I18nService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);

  readonly profile = input.required<UserProfile>();
  readonly showIdentity = input(false);
  readonly profileChange = output<UserProfile>();
  readonly editing = signal(false);
  private readonly fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');
  private readonly cameraButton = viewChild.required<ElementRef<HTMLButtonElement>>('cameraButton');

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

  constructor() {
    this.destroyRef.onDestroy(() => this.clearPick());
  }

  get initials(): string {
    const profile = this.profile();
    return `${profile.firstName.charAt(0)}${profile.lastName.charAt(0)}`.toUpperCase();
  }

  edit(): void {
    if (this.uploading() || this.removing()) return;
    this.editing.set(true);
    this.error.set(null);
    this.notice.set(null);
    // Allow choosing the same file again after cancelling or saving it.
    const input = this.fileInput().nativeElement;
    input.value = '';
    input.click();
  }

  pick(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.editing.set(true);
    this.reset();

    if (!file) return;

    if (!ACCEPTED_TYPES.includes(file.type)) {
      this.error.set(this.i18n.translate('profile.picture.invalidType'));
      input.value = '';
      return;
    }

    if (file.size > MAX_BYTES) {
      this.error.set(this.i18n.translate('profile.picture.tooLarge', {
        size: (file.size / 1024 / 1024).toFixed(1), limit: this.maxMegabytes,
      }));
      input.value = '';
      return;
    }

    this.file = file;
    this.preview.set(URL.createObjectURL(file));
  }

  upload(): void {
    if (!this.file || this.uploading() || this.removing()) return;

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
          this.notice.set(this.i18n.translate('profile.picture.updated'));
          this.finishEditing();
        }
      },
      error: (error: HttpErrorResponse) => {
        const body = error.error as ApiError | undefined;
        this.uploading.set(false);
        this.error.set(body?.error?.message ?? this.i18n.translate('profile.picture.uploadError'));
      },
    });
  }

  remove(): void {
    if (this.removing() || this.uploading()) return;

    this.removing.set(true);
    this.error.set(null);

    this.userService.deleteAvatar().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.removing.set(false);
        this.notice.set(this.i18n.translate('profile.picture.removed'));
        this.profileChange.emit({ ...this.profile(), avatarUrl: null });
        this.clearPick();
        this.finishEditing();
      },
      error: (error: HttpErrorResponse) => {
        const body = error.error as ApiError | undefined;
        this.removing.set(false);
        this.error.set(body?.error?.message ?? this.i18n.translate('profile.picture.removeError'));
      },
    });
  }

  cancel(): void {
    if (this.uploading() || this.removing()) return;
    this.reset();
    this.finishEditing();
  }

  private finishEditing(): void {
    this.editing.set(false);
    afterNextRender(() => this.cameraButton().nativeElement.focus(), { injector: this.injector });
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
