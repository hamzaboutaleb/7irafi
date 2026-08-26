import { Component, signal } from '@angular/core';

@Component({
  selector: 'app-how-it-works',
  imports: [],
  templateUrl: './how-it-works.html',
  styleUrl: './how-it-works.css',
})
export class HowItWorks {
  steps = signal([
    { number: 1, title: 'Post your problem', description: 'Describe what you need in a few words' },
    { number: 2, title: 'Get matched', description: 'Professionals respond to your request' },
    { number: 3, title: 'Get it done', description: 'Choose the best pro and get the job done' },
  ]);
}
