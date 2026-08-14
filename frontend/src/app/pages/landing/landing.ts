import { Component } from '@angular/core';
import { Navbar } from "../../components/navbar/navbar";
import { Hero } from "../../components/hero/hero";
import { Categories } from "../../components/categories/categories";
import { HowItWorks } from "../../components/how-it-works/how-it-works";
import { Testimonials } from "../../components/testimonials/testimonials";
import { Footer } from "../../components/footer/footer";

@Component({
  selector: 'app-landing',
  imports: [Navbar, Hero, Categories, HowItWorks, Testimonials, Footer],
  templateUrl: './landing.html',
  styleUrl: './landing.css',
})
export class Landing {}
