import { Component, DestroyRef, ElementRef, HostListener, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { LucideCheck, LucideChevronDown } from '@lucide/angular';

export interface SelectOption<T extends string = string> {
  value: T;
  label: string;
  lang?: string;
  direction?: 'ltr' | 'rtl';
  disabled?: boolean;
}

/** A select-only combobox. Focus stays on the trigger while browsing options. */
@Component({
  selector: 'app-select',
  imports: [LucideCheck, LucideChevronDown],
  templateUrl: './select.html',
  host: { class: 'inline-block max-w-full' },
})
export class Select<T extends string = string> {
  readonly controlId = input.required<string>();
  readonly label = input.required<string>();
  readonly options = input.required<readonly SelectOption<T>[]>();
  readonly value = input.required<T>();
  readonly disabled = input(false);
  readonly invalid = input(false);
  readonly describedBy = input<string | null>(null);
  readonly compact = input(false);
  readonly displayLabel = input<string>();
  readonly valueChange = output<T>();
  readonly selected = computed(() => this.options().find(option => option.value === this.value()));
  readonly open = signal(false);
  readonly active = signal(-1);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  private readonly panel = viewChild.required<ElementRef<HTMLElement>>('panel');
  private search = '';
  private searchedAt = 0;

  constructor() {
    const document = this.host.nativeElement.ownerDocument;
    const onScroll = (event: Event) => {
      if (this.open() && event.target !== this.panel().nativeElement) this.positionPanel();
    };
    document.addEventListener('scroll', onScroll, true);
    inject(DestroyRef).onDestroy(() => document.removeEventListener('scroll', onScroll, true));
  }

  toggle(): void {
    if (this.open()) this.close();
    else this.show();
  }

  private show(last = false): void {
    if (this.disabled()) return;
    const available = this.options().flatMap((option, index) => option.disabled ? [] : [index]);
    if (!available.length) return;
    const current = this.options().findIndex(option => option.value === this.value() && !option.disabled);
    this.active.set(current >= 0 ? current : available[last ? available.length - 1 : 0]);
    this.open.set(true);
    const panel = this.panel().nativeElement;
    // The top layer keeps menus clear of scrolling mobile navigation and dialogs.
    panel.showPopover();
    this.positionPanel();
    this.revealActive();
  }

  private positionPanel(): void {
    const panel = this.panel().nativeElement;
    const rect = this.trigger().nativeElement.getBoundingClientRect();
    const viewport = this.host.nativeElement.ownerDocument.documentElement;
    if (rect.bottom < 0 || rect.top > viewport.clientHeight) { this.close(); return; }
    const width = Math.min(Math.max(rect.width, 176), viewport.clientWidth - 16);
    const rtl = getComputedStyle(this.host.nativeElement).direction === 'rtl';
    panel.style.width = `${width}px`;
    panel.style.left = `${Math.max(8, Math.min(rtl ? rect.left : rect.right - width, viewport.clientWidth - width - 8))}px`;
    const below = viewport.clientHeight - rect.bottom - 16;
    const above = rect.top - 16;
    const upwards = below < Math.min(panel.scrollHeight, 240) && above > below;
    panel.style.maxHeight = `${Math.max(0, Math.min(280, upwards ? above : below))}px`;
    panel.style.top = `${upwards ? rect.top - panel.getBoundingClientRect().height - 8 : rect.bottom + 8}px`;
  }

  close(): void {
    this.panel().nativeElement.hidePopover();
    this.open.set(false);
    this.search = '';
  }

  choose(index: number): void {
    const option = this.options()[index];
    if (!option || option.disabled || this.disabled()) return;
    this.valueChange.emit(option.value);
    this.close();
    this.trigger().nativeElement.focus();
  }

  onKeydown(event: KeyboardEvent): void {
    if (this.disabled()) return;
    const key = event.key;
    if (key === 'Tab') { this.close(); return; }
    if (key === 'Escape' && this.open()) {
      event.preventDefault();
      event.stopPropagation();
      this.close();
      return;
    }
    if (['ArrowDown', 'ArrowUp', 'Home', 'End', 'Enter', ' '].includes(key)) {
      event.preventDefault();
      if (!this.open()) { this.show(key === 'ArrowUp' || key === 'End'); return; }
      if (key === 'Enter' || key === ' ') { this.choose(this.active()); return; }
      const available = this.options().flatMap((option, index) => option.disabled ? [] : [index]);
      const position = available.indexOf(this.active());
      const next = key === 'Home' ? 0 : key === 'End' ? available.length - 1
        : (position + (key === 'ArrowUp' ? -1 : 1) + available.length) % available.length;
      this.active.set(available[next]);
      this.revealActive();
    } else if (key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey) {
      event.preventDefault();
      if (!this.open()) this.show();
      const now = Date.now();
      this.search = (now - this.searchedAt < 600 ? this.search : '') + key.toLocaleLowerCase();
      this.searchedAt = now;
      const query = [...this.search].every(character => character === this.search[0]) ? this.search[0] : this.search;
      const options = this.options();
      for (let offset = 1; offset <= options.length; offset++) {
        const index = (this.active() + offset) % options.length;
        if (!options[index].disabled && options[index].label.toLocaleLowerCase().startsWith(query)) {
          this.active.set(index);
          this.revealActive();
          break;
        }
      }
    }
  }

  private revealActive(): void {
    const panel = this.panel().nativeElement;
    const row = panel.children[this.active()] as HTMLElement | undefined;
    if (row) panel.scrollTop = Math.max(0, Math.min(panel.scrollTop, row.offsetTop), row.offsetTop + row.offsetHeight - panel.clientHeight);
  }

  @HostListener('document:pointerdown', ['$event'])
  outside(event: PointerEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) this.close();
  }

  @HostListener('focusout', ['$event'])
  blur(event: FocusEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.relatedTarget as Node | null)) this.close();
  }

  @HostListener('window:resize')
  viewportChanged(): void {
    if (this.open()) this.close();
  }
}
