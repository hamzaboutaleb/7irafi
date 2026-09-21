import { AfterViewInit, Component, ElementRef, input, output, viewChild } from '@angular/core';

@Component({
  selector: 'app-posting-status-modal',
  templateUrl: './posting-status-modal.html',
})
export class PostingStatusModal implements AfterViewInit {
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  readonly heading = input.required<string>();
  readonly message = input.required<string>();
  readonly errors = input<string[]>([]);
  readonly canRetry = input(false);
  readonly busy = input(false);
  readonly retry = output<void>();
  readonly closed = output<void>();

  ngAfterViewInit(): void {
    this.dialog().nativeElement.showModal();
  }
}
