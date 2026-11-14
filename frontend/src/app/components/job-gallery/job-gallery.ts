import {
  Component,
  computed,
  ElementRef,
  input,
  OnDestroy,
  signal,
  viewChild,
} from '@angular/core';
import { LucideChevronLeft, LucideChevronRight, LucideMaximize2, LucideX } from '@lucide/angular';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { JobImage } from '../../types/job.js';

@Component({
  selector: 'app-job-gallery',
  imports: [TranslatePipe, LucideMaximize2, LucideX, LucideChevronLeft, LucideChevronRight],
  templateUrl: './job-gallery.html',
  styleUrl: './job-gallery.css',
})
export class JobGallery implements OnDestroy {
  private readonly imageDialog = viewChild.required<ElementRef<HTMLDialogElement>>('imageDialog');
  private readonly failedImages = signal<ReadonlySet<string>>(new Set());

  readonly images = input.required<readonly JobImage[]>();
  readonly title = input.required<string>();

  readonly selectedImage = signal(0);
  readonly visibleImages = computed(() =>
    this.images().filter((image) => !this.failedImages().has(image.id)),
  );
  readonly imageUrl = computed(() => this.visibleImages()[this.selectedImage()]?.url ?? null);

  ngOnDestroy(): void {
    this.imageDialog().nativeElement.close();
  }

  openImage(): void {
    if (!this.imageUrl()) return;
    this.imageDialog().nativeElement.showModal();
  }

  changeImage(direction: number): void {
    const count = this.visibleImages().length;
    if (count < 2) return;
    this.selectedImage.update((index) => (index + direction + count) % count);
  }

  onImageError(event: Event): void {
    const source = (event.target as HTMLImageElement).getAttribute('src');
    const failed = this.images()
      .filter((image) => image.url === source)
      .map((image) => image.id);
    this.failedImages.update((current) => new Set([...current, ...failed]));
    this.selectedImage.set(0);
    if (!this.visibleImages().length) this.imageDialog().nativeElement.close();
  }
}
