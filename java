const SUPABASE_URL = "https://qrugfdvdhaxvjqtruzzq.supabase.co";
const SUPABASE_KEY = "sb_publishable_ZV5TQ1ywOUmB2hPM5DZtnQ_Sgt77oq6";
const sb = supabase.createClient(SUPABASE_URL, SUPABASE_KEY);

const products = [
    { id: 1, name: 'ORC Hoodie', price: 60.00, emoji: '🧥' },
    { id: 2, name: 'ORC Tee', price: 35.00, emoji: '👕' },
    { id: 3, name: 'ORC Sweats', price: 50.00, emoji: '👖' }
];

let currentUser = null;
let cartItems = [];

window.addEventListener('DOMContentLoaded', async () => {
    renderProducts(products);
    const { data: { session } } = await sb.auth.getSession();
    if (session) {
        currentUser = session.user;
        updateUI();
        syncCart();
    }
    document.getElementById('login-form').addEventListener('submit', handleLogin);
    document.getElementById('signup-form').addEventListener('submit', handleSignup);
});

async function handleSignup(e) {
    e.preventDefault();
    const email = document.getElementById('signup-email').value;
    const password = document.getElementById('signup-password').value;
    const { error } = await sb.auth.signUp({ email, password });
    if (error) document.getElementById('signup-error').innerText = error.message;
    else { showToast("Success! Check your email."); closeModal('login-modal'); }
}

async function handleLogin(e) {
    e.preventDefault();
    const email = document.getElementById('login-email').value;
    const password = document.getElementById('login-password').value;
    const { data, error } = await sb.auth.signInWithPassword({ email, password });
    if (error) document.getElementById('login-error').innerText = error.message;
    else {
        currentUser = data.user;
        updateUI();
        syncCart();
        closeModal('login-modal');
        showToast("Logged in!");
    }
}

async function syncCart() {
    if (!currentUser) return;
    const { data } = await sb.from('cart_items').select('*').eq('user_id', currentUser.id);
    cartItems = data || [];
    updateCartDisplay();
}

async function addToCart(id) {
    if (!currentUser) return showLoginModal();
    const p = products.find(x => x.id === id);
    const { data, error } = await sb.from('cart_items').insert([{ 
        user_id: currentUser.id, product_id: p.id, product_name: p.name, price: p.price 
    }]).select();
    if (!error) { cartItems.push(data[0]); updateCartDisplay(); showToast("Added!"); }
}

async function processCheckout() {
    if (cartItems.length === 0) return;
    const total = cartItems.reduce((sum, i) => sum + i.price, 0);
    
    // 1. Create Order record
    const { error: orderError } = await sb.from('orders').insert([{
        user_id: currentUser.id,
        items: cartItems,
        total_amount: total
    }]);

    if (!orderError) {
        // 2. Clear cart in DB
        await sb.from('cart_items').delete().eq('user_id', currentUser.id);
        cartItems = [];
        updateCartDisplay();
        closeModal('cart-modal');
        showToast("Order Successful! Wear the difference.");
    } else {
        showToast("Checkout failed. Try again.");
    }
}

function renderProducts(list) {
    document.getElementById('products-container').innerHTML = list.map(p => `
        <div class="product-card">
            <div style="font-size:3rem">${p.emoji}</div>
            <h3>${p.name}</h3>
            <p>$${p.price.toFixed(2)}</p>
            <button class="add-to-cart" onclick="addToCart(${p.id})">Add to Cart</button>
        </div>
    `).join('');
}

function updateCartDisplay() {
    document.getElementById('cart-count').innerText = cartItems.length;
    const list = document.getElementById('cart-items-list');
    let total = 0;
    list.innerHTML = cartItems.map(i => {
        total += i.price;
        return `<div style="display:flex; justify-content:space-between; margin-bottom:10px;">
            <span>${i.product_name}</span><span>$${i.price}</span>
        </div>`;
    }).join('');
    document.getElementById('cart-total').innerText = total.toFixed(2);
    document.getElementById('checkout-btn').disabled = cartItems.length === 0;
}

function updateUI() {
    const menu = document.getElementById('auth-menu');
    menu.innerHTML = currentUser 
        ? `<a href="#" class="nav-link" onclick="sb.auth.signOut().then(() => location.reload())">Logout</a>`
        : `<a href="#" class="nav-link" onclick="showLoginModal()">Login</a>`;
}

function showLoginModal() { document.getElementById('login-modal').classList.add('active'); }
function openCartModal() { document.getElementById('cart-modal').classList.add('active'); updateCartDisplay(); }
function closeModal(id) { document.getElementById(id).classList.remove('active'); }
function showToast(msg) {
    const t = document.getElementById('success-message');
    document.getElementById('success-text').innerText = msg;
    t.classList.add('active');
    setTimeout(() => t.classList.remove('active'), 3000);
}
