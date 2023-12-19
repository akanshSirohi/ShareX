// Function to calculate the sum
function calculateSum() {
    // Get the values from the input fields
    var num1 = parseFloat(document.getElementById('num1').value);
    var num2 = parseFloat(document.getElementById('num2').value);

    // Check if the input values are valid numbers
    if (!isNaN(num1) && !isNaN(num2)) {
        // Calculate the sum
        var sum = num1 + num2;

        // Display the result
        document.getElementById('result').innerText = 'Result: ' + sum;
    } else {
        // Display an error message if the input values are not valid numbers
        document.getElementById('result').innerText = 'Please enter valid numbers';
    }
}
