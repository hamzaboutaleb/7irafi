import {
  Component,
  computed,
  inject,
  input,
  OnChanges,
  output,
  signal,
  SimpleChanges,
} from '@angular/core';
import {
  LucideCheck,
  LucideChevronDown,
  LucideMapPin,
  LucideMessageSquare,
  LucideUserRound,
  LucideWrench,
} from '@lucide/angular';

import { TranslationKey } from '../../i18n/language.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { JobApplication } from '../../types/application.js';
import { PublicProfessionalProfile } from '../../types/professional-profile.js';
import { PublicUserProfile } from '../../types/user.js';
import { jobDisplayStatusLabels } from '../../utils/job.js';

@Component({
  selector: 'app-applicant-card',
  imports: [
    TranslatePipe,
    LucideCheck,
    LucideChevronDown,
    LucideMapPin,
    LucideMessageSquare,
    LucideUserRound,
    LucideWrench,
  ],
  templateUrl: './applicant-card.html',
  styleUrl: './applicant-card.css',
})
export class ApplicantCard implements OnChanges {
  private readonly i18n = inject(I18nService);

  readonly application = input.required<JobApplication>();
  readonly profile = input<PublicProfessionalProfile | null>(null);
  readonly person = input<PublicUserProfile | null>(null);
  readonly showAccept = input(false);
  readonly accepting = input<string | null>(null);
  readonly opening = input<string | null>(null);
  readonly refreshing = input(false);
  readonly messageError = input<TranslationKey | null>(null);

  readonly acceptRequested = output<void>();
  readonly messageRequested = output<void>();
  readonly profileRetryRequested = output<void>();

  readonly expanded = signal(false);
  readonly imageFailed = signal(false);
  readonly name = computed(() => {
    const person = this.person();
    return (
      (person ? `${person.firstName} ${person.lastName}`.trim() : this.profile()?.firstName) ||
      this.i18n.translate('chat.applicant')
    );
  });
  readonly initials = computed(() => {
    const person = this.person();
    return (
      person
        ? person.firstName.charAt(0) + person.lastName.charAt(0)
        : (this.profile()?.firstName.charAt(0) ?? '')
    ).toUpperCase();
  });
  readonly avatar = computed(() =>
    this.imageFailed() ? null : this.person()?.avatarUrl || this.profile()?.avatarUrl || null,
  );
  readonly dateLabel = computed(() =>
    new Intl.DateTimeFormat(this.i18n.language(), {
      dateStyle: 'medium',
    }).format(new Date(this.application().createdAt)),
  );
  readonly statusLabel = computed(() =>
    this.i18n.translate(
      this.application().status === 'PENDING'
        ? 'applicants.pending'
        : jobDisplayStatusLabels[this.application().status],
    ),
  );

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['profile'] || changes['person']) this.imageFailed.set(false);
  }
}
