import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Navbar } from '../../components/navbar/navbar';
import { Hero } from '../../components/hero/hero';
import { Categories } from '../../components/categories/categories';
import { HowItWorks } from '../../components/how-it-works/how-it-works';
import { Footer } from '../../components/footer/footer';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

@Component({
  selector: 'app-landing',
  imports: [Navbar, Hero, Categories, HowItWorks, Footer, RouterLink, TranslatePipe],
  templateUrl: './landing.html',
  styleUrl: './landing.css',
})
export class Landing {}
