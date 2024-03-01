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

* galapagos@hermesworld.com

Our colleagues at Hermes can also use the Teams Channel "Kafka @ Hermes" to contact us.


## How to change code

Fork the project in your account and create a branch with your fix or new feature: `feat_great-greater`
or `bugfix_wrong-pixel`.

We recommend to add the main galapagos repository as `upstream` remote:

```bash
git remote add upstream https://github.com/HermesGermany/galapagos.git
git fetch upstream
```

Then, use standard git mechanisms to create a branch and work on your feature or bugfix:

```bash
git checkout -b feat_great-greater
```

Commit your changes in that branch, writing the code following the code style.

To incorporate latest changes, you should merge `upstream/main` or, if necessary, rebase already existing commits on
your branch:

```bash
git fetch upstream
git rebase upstream/main
```

* Edit or create tests which prove your fix or newly added functionality.
* Always edit or create documentation.
* Finally, create a Pull Request in the Galapagos GitHub project, using your branch as the source and Galapagos' `main`
  branch as the target.

### Commit message format

To ensure a unified view to the changes please adhere
to [seven rules for Commit Messages](https://chris.beams.io/posts/git-commit/#seven-rules).
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

### Wait for feedback

Before accepting your contributions, we will review them. You may get feedback about what should be 
fixed in your modified code. If so, just keep committing in your branch and the pull request will be 
updated automatically.

We will only accept pull requests that have zero failing checks.


### The merge

Finally, your contributions will be merged. Currently, no snapshot builds are automatically deployed to the public, but
we will perform a new release of Galapagos every three to four months. The next release will include your changes then.
