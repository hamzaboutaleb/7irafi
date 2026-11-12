import { AfterViewInit, Component, ElementRef, input, output, viewChild } from '@angular/core';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

@Component({
  selector: 'app-posting-status-modal',
  imports: [TranslatePipe],
  templateUrl: './posting-status-modal.html',
})
export class PostingStatusModal implements AfterViewInit {
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  readonly heading = input.required<string>();
  readonly message = input.required<string>();
  readonly errors = input<string[]>([]);
  readonly closed = output<void>();

  ngAfterViewInit(): void {
    this.dialog().nativeElement.showModal();
  }
}
