import {
  Component,
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

const PANEL_MARGIN = 16;
const NARROW_SCREEN_BREAKPOINT = 1280;

@Component({
  selector: 'app-notification-bell',
  imports: [LucideBell, TranslatePipe, NotificationPanel],
  templateUrl: './notification-bell.html',
  styleUrl: './notification-bell.css',
})
export class NotificationBell {
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly controlId = input.required<string>();
  readonly opened = output<void>();

  readonly open = signal(false);
  readonly unread = this.notifications.unread;

  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');

  private readonly panel = viewChild.required<ElementRef<HTMLElement>>('panel');

  constructor() {
    this.closeOnNavigation();
  }

  toggle(): void {
    if (this.open()) {
      this.close();
      return;
    }

    this.showPanel();
  }

  close(restoreFocus = false): void {
    if (!this.open()) {
      return;
    }

    this.panel().nativeElement.hidePopover();
    this.open.set(false);

    if (restoreFocus) {
      this.trigger().nativeElement.focus();
    }
  }

  @HostListener('document:pointerdown', ['$event'])
  @HostListener('document:focusin', ['$event'])
  closeIfOutside(event: Event): void {
    if (!this.open()) {
      return;
    }

    const isInside = this.host.nativeElement.contains(event.target as Node);

    if (!isInside) {
      this.close();
    }
  }

  @HostListener('keydown.escape', ['$event'])
  escape(event: Event): void {
    if (!this.open()) {
      return;
    }

    event.preventDefault();
    event.stopPropagation();
    this.close(true);
  }

  @HostListener('window:resize')
  resize(): void {
    this.close();
  }

  private closeOnNavigation(): void {
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationStart),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.close());
  }

  private showPanel(): void {
    this.opened.emit();
    this.open.set(true);

    const panel = this.panel().nativeElement;

    panel.showPopover();
    this.positionPanel(panel);
    panel.focus();

    if (this.notifications.loadError() && !this.notifications.loading()) {
      this.notifications.load();
    }
  }

  private positionPanel(panel: HTMLElement): void {
    const position = this.calculatePanelPosition();

    panel.style.width = `${position.width}px`;
    panel.style.left = `${position.left}px`;
    panel.style.top = `${position.top}px`;
  }

  private calculatePanelPosition(): { width: number; left: number; top: number } {
    const button = this.trigger().nativeElement.getBoundingClientRect();
    const screenWidth = this.host.nativeElement.ownerDocument.documentElement.clientWidth;

    const maximumWidth = 400;
    const availableWidth = screenWidth - PANEL_MARGIN * 2;
    const width = Math.min(maximumWidth, availableWidth);

    const left = this.calculatePanelLeft(button, screenWidth, width);
    const top = this.calculatePanelTop(button.bottom, screenWidth);

    return { width, left, top };
  }

  private calculatePanelLeft(button: DOMRect, screenWidth: number, panelWidth: number): number {
    const isNarrowScreen = screenWidth < NARROW_SCREEN_BREAKPOINT;
    const isRtl = getComputedStyle(this.host.nativeElement).direction === 'rtl';

    let left: number;

    if (isNarrowScreen) {
      left = (screenWidth - panelWidth) / 2;
    } else if (isRtl) {
      left = button.left;
    } else {
      left = button.right - panelWidth;
    }

    const maximumLeft = screenWidth - panelWidth - PANEL_MARGIN;

    return Math.max(PANEL_MARGIN, Math.min(left, maximumLeft));
  }

  private calculatePanelTop(buttonBottom: number, screenWidth: number): number {
    const gap = 8;

    if (screenWidth >= NARROW_SCREEN_BREAKPOINT) {
      return buttonBottom + gap;
    }

    const navbar = this.host.nativeElement.closest('.navbar__inner');
    const navbarBottom = navbar?.getBoundingClientRect().bottom;

    return (navbarBottom ?? buttonBottom) + gap;
  }
}
