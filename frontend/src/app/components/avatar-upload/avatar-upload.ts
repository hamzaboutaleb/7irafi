import { HttpErrorResponse, HttpEventType } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, Injector, afterNextRender, inject, input, output, signal, viewChild } from '@angular/core';
import { LucideCamera } from '@lucide/angular';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { UserService } from '../../services/user.js';
import { ApiError } from '../../types/api.js';
import { UserProfile } from '../../types/user.js';

// The picture types we accept, and the 2 MB size limit.
const ACCEPTED_TYPES = ['image/png', 'image/jpeg', 'image/webp'];
const MAX_BYTES = 2 * 1024 * 1024;

// Pick, preview, upload and remove the profile picture.
// The checks here only give quick feedback: the server checks everything again.
@Component({
  selector: 'app-avatar-upload',
  imports: [TranslatePipe, LucideCamera],
  templateUrl: './avatar-upload.html',
  styleUrl: './avatar-upload.css',
})
export class AvatarUpload {
  // services
  private readonly userService = inject(UserService);
  private readonly i18n = inject(I18nService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);

  // inputs from the parent page
  readonly profile = input.required<UserProfile>();
  readonly showIdentity = input(false);
  // tells the parent page the picture changed
  readonly profileChange = output<UserProfile>();

  // elements in the HTML
  private readonly fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');
  private readonly cameraButton = viewChild.required<ElementRef<HTMLButtonElement>>('cameraButton');

  // text for the HTML
  readonly accept = ACCEPTED_TYPES.join(',');
  readonly maxMegabytes = MAX_BYTES / (1024 * 1024);

  // page state
  readonly editing = signal(false);
  readonly preview = signal<string | null>(null); // the picked picture, shown before upload
  readonly uploading = signal(false);
  readonly progress = signal(0);
  readonly removing = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);

  // the file waiting to be uploaded
  private file: File | null = null;

  // free the preview when the page closes
  constructor() {
    this.destroyRef.onDestroy(() => this.clearPick());
  }

  // letters shown when there is no picture
  get initials(): string {
    const profile = this.profile();
    return `${profile.firstName.charAt(0)}${profile.lastName.charAt(0)}`.toUpperCase();
  }

  // camera button: open the file picker
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

  // a file was chosen: check it and show it
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

  // Save button
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

  // Remove button
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

  // Cancel button
  cancel(): void {
    if (this.uploading() || this.removing()) return;
    this.reset();
    this.finishEditing();
  }

  // close edit mode and focus the camera button again
  private finishEditing(): void {
    this.editing.set(false);
    afterNextRender(() => this.cameraButton().nativeElement.focus(), { injector: this.injector });
  }

  // forget the picked file and its preview
  private clearPick(): void {
    const preview = this.preview();
    if (preview) URL.revokeObjectURL(preview);

    this.file = null;
    this.preview.set(null);
    this.progress.set(0);
  }

  // clear messages and the picked file
  private reset(): void {
    this.error.set(null);
    this.notice.set(null);
    this.clearPick();
  }
}
