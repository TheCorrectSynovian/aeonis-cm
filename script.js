/**
 * Aeonis: Command Master - Website Scripts
 * Smooth scrolling, animations, and interactive elements
 */

// DOM Elements
const navbar = document.querySelector('.navbar');
const navToggle = document.querySelector('.nav-toggle');
const navMenu = document.querySelector('.nav-menu');
const navLinks = document.querySelectorAll('.nav-link');
const fadeElements = document.querySelectorAll('.fade-in');

// ============================================
// Mobile Navigation Toggle
// ============================================
if (navToggle && navMenu) {
    navToggle.addEventListener('click', () => {
        navToggle.classList.toggle('active');
        navMenu.classList.toggle('active');
    });

    // Close menu when clicking a link
    navLinks.forEach(link => {
        link.addEventListener('click', () => {
            navToggle.classList.remove('active');
            navMenu.classList.remove('active');
        });
    });
}

// ============================================
// Smooth Scrolling for Navigation Links
// ============================================
navLinks.forEach(link => {
    link.addEventListener('click', (e) => {
        e.preventDefault();
        const targetId = link.getAttribute('href');
        const targetSection = document.querySelector(targetId);
        
        if (targetSection) {
            const navHeight = navbar ? navbar.offsetHeight : 0;
            const targetPosition = targetSection.offsetTop - navHeight;
            
            window.scrollTo({
                top: targetPosition,
                behavior: 'smooth'
            });
        }
    });
});

// ============================================
// Navbar Background on Scroll
// ============================================
let lastScroll = 0;

window.addEventListener('scroll', () => {
    const currentScroll = window.pageYOffset;
    
    // Add shadow on scroll
    if (navbar) {
        if (currentScroll > 50) {
            navbar.style.boxShadow = '0 4px 20px rgba(0, 0, 0, 0.5)';
        } else {
            navbar.style.boxShadow = 'none';
        }
    }
    
    lastScroll = currentScroll;
});

// ============================================
// Fade-in Animation on Scroll
// ============================================
const observerOptions = {
    root: null,
    rootMargin: '0px',
    threshold: 0.1
};

const fadeObserver = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
        if (entry.isIntersecting) {
            entry.target.classList.add('visible');
            // Optional: Stop observing after animation
            // fadeObserver.unobserve(entry.target);
        }
    });
}, observerOptions);

// Observe all fade-in elements
fadeElements.forEach(element => {
    fadeObserver.observe(element);
});

// ============================================
// Active Navigation Link Highlight
// ============================================
const sections = document.querySelectorAll('section[id]');

function highlightNavLink() {
    const scrollY = window.pageYOffset;
    
    sections.forEach(section => {
        const sectionHeight = section.offsetHeight;
        const sectionTop = section.offsetTop - 100;
        const sectionId = section.getAttribute('id');
        const navLink = document.querySelector(`.nav-link[href="#${sectionId}"]`);
        
        if (navLink) {
            if (scrollY > sectionTop && scrollY <= sectionTop + sectionHeight) {
                navLinks.forEach(link => link.classList.remove('active'));
                navLink.classList.add('active');
            }
        }
    });
}

window.addEventListener('scroll', highlightNavLink);

// ============================================
// Gallery Image Lightbox (Optional Enhancement)
// ============================================
const galleryItems = document.querySelectorAll('.gallery-item');

galleryItems.forEach(item => {
    item.addEventListener('click', () => {
        const img = item.querySelector('img');
        if (img && img.complete && img.naturalWidth > 0) {
            // Simple alert for now - can be enhanced with a modal
            console.log('Image clicked:', img.alt);
        }
    });
});

// ============================================
// Smooth Scroll for CTA Button
// ============================================
const ctaButtons = document.querySelectorAll('.cta-button[href^="#"]');

ctaButtons.forEach(button => {
    button.addEventListener('click', (e) => {
        e.preventDefault();
        const targetId = button.getAttribute('href');
        const targetSection = document.querySelector(targetId);
        
        if (targetSection) {
            const navHeight = navbar ? navbar.offsetHeight : 0;
            const targetPosition = targetSection.offsetTop - navHeight;
            
            window.scrollTo({
                top: targetPosition,
                behavior: 'smooth'
            });
        }
    });
});

// ============================================
// Typing Effect for Hero (Optional)
// ============================================
function typeEffect(element, text, speed = 50) {
    let i = 0;
    element.innerHTML = '';
    
    function type() {
        if (i < text.length) {
            element.innerHTML += text.charAt(i);
            i++;
            setTimeout(type, speed);
        }
    }
    
    type();
}

// ============================================
// Add Stagger Animation to Cards
// ============================================
function addStaggerAnimation() {
    const cards = document.querySelectorAll('.card, .step-card, .download-card, .compat-card, .attack-card');
    
    cards.forEach((card, index) => {
        card.style.transitionDelay = `${index * 0.1}s`;
    });
}

// Initialize stagger animation
addStaggerAnimation();

// ============================================
// Console Welcome Message
// ============================================
console.log('%c⚡ Aeonis: Command Master ⚡', 'color: #00d9ff; font-size: 24px; font-weight: bold;');
console.log('%cTake control. Command anything. Master the game.', 'color: #b347ff; font-size: 14px;');
console.log('%cMade by Quantum Creeper (Niman)', 'color: #ffd700; font-size: 12px;');

// ============================================
// Initialize on DOM Load
// ============================================
document.addEventListener('DOMContentLoaded', () => {
    // Trigger initial fade-in check
    highlightNavLink();
    
    // Add loaded class to body for any CSS animations
    document.body.classList.add('loaded');
});

// ============================================
// Handle Image Load Errors
// ============================================
document.querySelectorAll('img').forEach(img => {
    img.addEventListener('error', function() {
        // Add a placeholder background if image fails to load
        this.style.opacity = '0.5';
        this.parentElement.style.background = 'linear-gradient(135deg, #1a1a25, #2a2a35)';
    });
});
