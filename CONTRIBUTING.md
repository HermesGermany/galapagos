# Contributing

So, you want to contribute to this project. That's awesome! However, before doing so, please read the following simple steps how to contribute.


## Always open an issue

Before doing anything always [open an issue](https://github.com/HermesGermany/galapagos/issues), 
describing the contribution you would like to make, the bug you found or any other ideas you have. 
This will help us to get you started on the right foot.

It is recommended to wait for feedback before continuing to next steps. However, if 
the issue is clear (e.g. a typo) and the fix is simple, you can continue and fix it.


## Contact the galapagos core team

Feel free to contact us if you have any questions or need help!

* galapagos@hermesworld.com (Florian, Emre, Gunnar)

Our colleagues at Hermes can also use the chat channel "galapagos" to contact us.


## How to change code

Fork the project in your account and create a branch with your fix or new feature: some-great-feature or some-issue-fix.

Your branch should track the `next_minor` branch, **not** the `main` branch!
We recommend to add the main galapagos repository as `upstream` remote:

```bash
git remote add upstream https://github.com/HermesGermany/galapagos.git
git fetch upstream
```

Then, you can create your feature branch to track `upstream/next_minor`:

```bash
git checkout -b feature_my_cool_feature upstream/next_minor
```

Commit your changes in that branch, writing the code following the code style.

* Edit or create tests which prove your fix or newly added functionality.
* Always edit or create documentation.

### Commit message format

To ensure a unified view to the changes please adhere to [seven rules for Commit Messages](https://chris.beams.io/posts/git-commit/#seven-rules).
Example:

    Write subject in imperative mood wihtout a period at the end
    
    - Separate subject from body with a blank line
    - Please use bulletpoints in the body
    - Typically a hyphen is used for the bullet, preceded
      by a single space, with blank lines in between, but conventions
      vary here
    
    Resolves: #123
    See also: #456, #789


### Create a pull request

Open a pull request, and reference the initial issue in the pull request message (e.g. Fixes #123456). 
Write a good description and title, so everybody will know what is fixed/improved.

Please make sure your Pull Request also selects `next_minor` as the target branch, **not** `main`!
(Don't worry if you miss this; this can easily be changed afterwards by editing the Pull Request.)

### Wait for feedback

Before accepting your contributions, we will review them. You may get feedback about what should be 
fixed in your modified code. If so, just keep committing in your branch and the pull request will be 
updated automatically.

We will only accept pull requests that have zero failing checks.


### The merge

Finally, your contributions will be merged. Currently, no snapshot builds are automatically deployed to the public, but we will perform a new release of Galapagos every three to four weeks. The next release will include your changes then.
