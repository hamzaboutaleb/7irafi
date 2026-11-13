import {
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { NavigationStart, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { LucideBell } from '@lucide/angular';
import { NotificationService } from '../../services/notification.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { NotificationPanel } from '../notification-panel/notification-panel.js';

@Component({
  selector: 'app-notification-bell',
  imports: [LucideBell, TranslatePipe, NotificationPanel],
  templateUrl: './notification-bell.html',
  styleUrl: './notification-bell.css',
})
export class NotificationBell {
  readonly controlId = input.required<string>();
  readonly opened = output<void>();
  readonly open = signal(false);
  private readonly notifications = inject(NotificationService);
  readonly unread = this.notifications.unread;
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  private readonly panel = viewChild.required<ElementRef<HTMLElement>>('panel');

  constructor() {
    inject(Router)
      .events.pipe(
        filter((event) => event instanceof NavigationStart),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe(() => this.close());
  }

  toggle(): void {
    if (this.open()) {
      this.close();
      return;
    }
    this.opened.emit();
    this.open.set(true);
    const panel = this.panel().nativeElement;
    panel.showPopover();
    const rect = this.trigger().nativeElement.getBoundingClientRect();
    const viewport = this.host.nativeElement.ownerDocument.documentElement;
    const mobile = viewport.clientWidth < 1280;
    const width = Math.min(400, viewport.clientWidth - 32);
    const rtl = getComputedStyle(this.host.nativeElement).direction === 'rtl';
    const left = mobile ? (viewport.clientWidth - width) / 2 : rtl ? rect.left : rect.right - width;
    panel.style.width = `${width}px`;
    panel.style.left = `${Math.max(16, Math.min(left, viewport.clientWidth - width - 16))}px`;
    panel.style.top = `${(mobile ? (this.host.nativeElement.closest('.navbar-inner')?.getBoundingClientRect().bottom ?? rect.bottom) : rect.bottom) + 8}px`;
    panel.focus();
    if (this.notifications.loadError() && !this.notifications.loading()) this.notifications.load();
  }

  close(restoreFocus = false): void {
    if (!this.open()) return;
    this.panel().nativeElement.hidePopover();
    this.open.set(false);
    if (restoreFocus) this.trigger().nativeElement.focus();
  }

  @HostListener('document:pointerdown', ['$event'])
  outside(event: PointerEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node)) this.close();
  }

  @HostListener('document:focusin', ['$event'])
  focusOutside(event: FocusEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) this.close();
  }

  @HostListener('keydown.escape', ['$event'])
  escape(event: Event): void {
    if (!this.open()) return;
    event.preventDefault();
    event.stopPropagation();
    this.close(true);
  }

  @HostListener('window:resize')
  resize(): void {
    this.close();
  }
}
