import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Footer } from '../../components/footer/footer';
import { HowItWorks } from '../../components/how-it-works/how-it-works';
import { Navbar } from '../../components/navbar/navbar';
import { AuthService } from '../../services/auth';

@Component({
  selector: 'app-how-it-works-page',
  imports: [Navbar, Footer, HowItWorks, RouterLink],
  templateUrl: './how-it-works-page.html',
})
export class HowItWorksPage {
  private readonly authService = inject(AuthService);

  readonly isAuthenticated = Boolean(this.authService.getToken());

  readonly professionalSteps = signal([
    {
      number: 1,
      title: 'Create your account',
      description: 'Sign up with your email. One account covers both hiring and working.',
    },
    {
      number: 2,
      title: 'Build your professional profile',
      description:
        'Write a short biography, pick the services you provide, and choose the cities you cover.',
    },
    {
      number: 3,
      title: 'Find work near you',
      description: 'Browse open jobs and filter them by service, city, and budget.',
    },
    {
      number: 4,
      title: 'Get hired',
      description: 'Apply to the jobs that fit, agree the details, and complete the work.',
    },
  ]);

  readonly answers = signal([
    {
      question: 'Does it cost anything to join?',
      answer:
        'No. Creating an account, posting a job, and building a professional profile are all free.',
    },
    {
      question: 'Can I be both a client and a professional?',
      answer:
        'Yes. Every account can post jobs. Completing a professional profile simply adds the ability to offer services as well.',
    },
    {
      question: 'Which cities are covered?',
      answer:
        'Professionals choose the Moroccan cities they serve, so the jobs you see are the ones near you.',
    },
    {
      question: 'How do I change my services or service areas later?',
      answer:
        'Open your profile and edit your professional profile at any time. Your changes take effect immediately.',
    },
  ]);
}
